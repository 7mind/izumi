package izumi.distage.testkit.runner.bootstrap

import izumi.distage.testkit.protocol.*
import izumi.distage.testkit.runner.*

import sbt.testing.{Event, EventHandler, Fingerprint, Framework as SbtFramework, Logger, OptionalThrowable, Runner, Selector, Status, SubclassFingerprint, SuiteSelector, Task, TaskDef, TestSelector}

import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.{ForkJoinPool, ForkJoinWorkerThread, TimeUnit}
import scala.concurrent.{Await, ExecutionContext, Promise}
import scala.concurrent.duration.Duration
import scala.util.control.NonFatal
import scala.util.Using

final class Framework extends SbtFramework {
  private val fingerprint = new SubclassFingerprint {
    override def isModule(): Boolean = false
    override def superclassName(): String = classOf[TestSuite].getName
    override def requireNoArgConstructor(): Boolean = true
  }

  override def name(): String = "distage"
  override def fingerprints(): Array[Fingerprint] = Array(fingerprint)
  override def runner(args: Array[String], remoteArgs: Array[String], testClassLoader: ClassLoader): Runner = {
    val invocation = ForkReceiptArguments.parse(args.toVector, remoteArgs.toVector)
    val request = RequestArguments.parse(invocation.arguments).fold(error => throw new IllegalArgumentException(error.message), value => value)
    val control = if (invocation.commandCompletion) invocation.hostDirectory.map(directory => BootstrapRunContext(directory, ForkProcessId(ProcessHandle.current().pid()), invocation.forked)) else None
    val runner = new BootstrapRunner(args.clone(), invocation.forwardedRemoteArguments.toArray, name => JvmSuiteLoader.load(name, testClassLoader), testClassLoader, request, invocation.eventDirectory, control)
    if (invocation.forked) {
      val directory = invocation.hostDirectory.getOrElse(throw new IllegalStateException("Fork receipt activation has no host ownership"))
      if (invocation.commandCompletion) {
        require(Option(System.getProperty(ForkCompletionOwnership.DIRECTORY_PROPERTY)).contains(directory.toString), "Command completion agent has no matching target ownership")
        runner
      } else {
        val TimeoutSeconds = 30L
        val PollMillis = 5L
        new ForkReceiptRunner(runner, FileForkReceiptStore.open(directory), ForkReceiptWaitPolicy(TimeUnit.SECONDS.toNanos(TimeoutSeconds), PollMillis))
      }
    } else runner
  }
}

private[bootstrap] final case class BootstrapRunContext(directory: Path, process: ForkProcessId, forked: Boolean)

private[bootstrap] final class BootstrapRunner(
  arguments: Array[String],
  remoteArguments: Array[String],
  factory: String => TestSuite,
  testClassLoader: ClassLoader,
  request: RunRequest,
  eventDirectory: Option[Path],
  controlDirectory: Option[BootstrapRunContext],
) extends Runner {
  private var spent = false
  private var activeTasks = 0

  override def args(): Array[String] = arguments.clone()
  override def remoteArgs(): Array[String] = remoteArguments.clone()

  override def tasks(definitions: Array[TaskDef]): Array[Task] = synchronized {
    requireActive()
    require(definitions.map(_.fullyQualifiedName()).distinct.length == definitions.length, "Duplicate suite task definitions")
    definitions.foreach { definition =>
      require(definition.selectors().forall(_.isInstanceOf[SuiteSelector]), "This bootstrap stage accepts suite selectors only")
      definition.fingerprint() match {
        case fingerprint: SubclassFingerprint =>
          require(!fingerprint.isModule() && fingerprint.superclassName() == classOf[TestSuite].getName, "Task fingerprint does not identify a distage suite")
        case _ => throw new IllegalArgumentException("Task fingerprint does not identify a distage suite")
      }
    }
    val invocation = new Invocation(request, definitions.toVector, factory, testClassLoader, eventDirectory, controlDirectory)
    invocation.projections.map { projection =>
      new Task {
        private var executed = false
        override def taskDef(): TaskDef = projection.definition
        override def tags(): Array[String] = Array.empty
        override def execute(handler: EventHandler, loggers: Array[Logger]): Array[Task] = {
          beginTask()
          val interruption = new TaskInterruption
          try {
            synchronized {
              require(!executed, "Suite task has already executed")
              executed = true
            }
            try {
              projection.attach(handler)
              invocation.result(interruption) match {
                case Right(outcome) =>
                  outcome.failures.foreach(projection.fail)
                  if (outcome.cancelled && outcome.failures.isEmpty) projection.fail(RunnerFailure.message(FailurePhase.Transport, "Host test run was cancelled"))
                case Left(cause) => projection.fail(RunnerFailure.fromThrowable(FailurePhase.Transport, cause))
              }
              projection.rethrowDeliveryFailure()
              interruption.rethrow()
            } finally projection.detach()
            Array.empty
          } finally {
            endTask()
          }
        }
      }: Task
    }.toArray
  }

  override def done(): String = synchronized {
    requireActive()
    spent = true
    while (activeTasks > 0) wait()
    ""
  }

  private def requireActive(): Unit = {
    if (spent) throw new IllegalStateException("Test runner is spent")
  }

  private def beginTask(): Unit = synchronized {
    requireActive()
    activeTasks += 1
  }

  private def endTask(): Unit = synchronized {
    activeTasks -= 1
    require(activeTasks >= 0, "Active task count became negative")
    notifyAll()
  }
}

private[bootstrap] final class Invocation(request: RunRequest, definitions: Vector[TaskDef], factory: String => TestSuite, testClassLoader: ClassLoader, eventDirectory: Option[Path], controlDirectory: Option[BootstrapRunContext]) {
  private final val ShutdownPollSeconds = 1L
  val projections: Vector[SuiteProjection] = definitions.map(new SuiteProjection(_))
  private val completion = Promise[Either[Throwable, RunOutcome]]()
  private val cancellation = new Cancellation
  private var started = false
  @volatile private var activeApplication = Option.empty[TestApplication]

  def result(interruption: TaskInterruption): Either[Throwable, RunOutcome] = {
    val launch = synchronized {
      if (started) false else { started = true; true }
    }
    if (launch) {
      val outcome = try execute(interruption) catch { case cause: Throwable => Left(cause) }
      val _ = completion.success(outcome)
    }
    interruption.await(Await.result(completion.future, Duration.Inf), () => cancel()).map { outcome =>
      val deliveryFailures = projections.flatMap(_.deliveryFailure).map(RunnerFailure.fromThrowable(FailurePhase.Transport, _))
      outcome.copy(failures = outcome.failures ++ deliveryFailures, cancelled = outcome.cancelled || cancellation.isRequested)
    }
  }

  private def cancel(): Unit = {
    cancellation.request()
    activeApplication.foreach(_.cancel())
  }

  private def execute(interruption: TaskInterruption): Either[Throwable, RunOutcome] = {
    val workerFactory = new ForkJoinPool.ForkJoinWorkerThreadFactory {
      override def newThread(pool: ForkJoinPool): ForkJoinWorkerThread = {
        val worker = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool)
        worker.setContextClassLoader(testClassLoader)
        worker
      }
    }
    val executor = new ForkJoinPool(Runtime.getRuntime.availableProcessors(), workerFactory, null, true)
    val executionContext = ExecutionContext.fromExecutorService(executor)
    try {
      val result = Using.Manager { use =>
        controlDirectory.filter(_.forked).foreach(context => { val _ = use(new ForkCancellation(context.directory, () => cancel())) })
        val factories = projections.map { projection => () => new TestSuite {
          override def register(context: RegistrationContext): RegisteredSuite = {
            val name = projection.definition.fullyQualifiedName()
            val registered = try factory(name).register(context)
            catch { case cause: LinkageError => throw new IllegalStateException("Cannot register suite " + name, cause) }
            projection.associate(registered.descriptor.id)
            registered
          }
        }}
        val run = RunId(UUID.randomUUID().toString)
        val frames = eventDirectory.map(directory => use(FileProtocolFrameSink.createNew(directory.resolve(run.value + ".jsonl"))))
        var terminal = Option.empty[RunOutcome]
        var finished = Option.empty[RunOutcome]
        val output = new ProtocolOutput {
          override def accept(message: ProtocolMessage): Unit = {
            frames.foreach(_.writeFrame(ProtocolCodec.encode(message)))
            message match {
              case ProtocolMessage.Event(_, RunEvent.TestCompleted(_, test)) =>
                val matching = projections.filter(_.owns(test.id.suite))
                require(matching.size == 1, "Test completion has no unique suite task owner")
                matching.head.complete(test)
                matching.head.requireDelivery()
              case ProtocolMessage.Event(_, RunEvent.Finished(_, outcome)) => finished = Some(outcome)
              case ProtocolMessage.Event(_, _) => ()
              case ProtocolMessage.Completed(outcome) =>
                require(terminal.isEmpty && finished.contains(outcome), "Application completion differs from its terminal event")
                terminal = Some(outcome)
              case ProtocolMessage.Rejected(_, failure) =>
                require(terminal.isEmpty, "Application emitted duplicate terminal responses")
                terminal = Some(RunOutcome(run, Vector.empty, Vector(failure), cancellation.isRequested))
              case _ => throw new IllegalStateException("Execution application emitted an unexpected response")
            }
          }
        }
        val application = new TestApplication(run, request.identity, factories, executionContext, output)
        activeApplication = Some(application)
        if (cancellation.isRequested) application.cancel()
        val execution = RunnerCompletion.after(application.accept(ProtocolMessage.Request(RequestOperation.Execute, run, request)), () => application.close())(executionContext)
        interruption.await(Await.result(execution, Duration.Inf), () => cancel())
        terminal.getOrElse(throw new IllegalStateException("Application returned without a terminal response"))
      }.toEither
      result.foreach { outcome =>
        controlDirectory.foreach(context => new FileForkRunReports(context.directory).publish(ForkRunReport(context.process, outcome, projections.map(_.owner))))
      }
      result
    }
    finally {
      activeApplication = None
      executionContext.shutdown()
      interruption.await({ while (!executionContext.awaitTermination(ShutdownPollSeconds, TimeUnit.SECONDS)) (); () }, () => cancel())
    }
  }
}

private[bootstrap] final class SuiteProjection(val definition: TaskDef) {
  private var suite = Option.empty[SuiteId]
  private var handler = Option.empty[EventHandler]
  private var buffered = Vector.empty[Event]
  private var closed = false
  private var callbackFailure = Option.empty[Throwable]

  def associate(id: SuiteId): Unit = synchronized {
    require(suite.isEmpty, "Suite task already has a logical identity")
    suite = Some(id)
  }

  def owns(id: SuiteId): Boolean = synchronized { suite.contains(id) }

  def owner: ForkSuiteOwner = synchronized { ForkSuiteOwner(definition.fullyQualifiedName(), suite) }

  def attach(value: EventHandler): Unit = synchronized {
    require(handler.isEmpty && !closed, "Suite task handler is already attached or closed")
    handler = Some(value)
    val pending = buffered
    buffered = Vector.empty
    pending.foreach(deliver)
  }

  def detach(): Unit = synchronized {
    handler = None
    closed = true
    buffered = Vector.empty
  }

  def deliveryFailure: Option[Throwable] = synchronized { callbackFailure }

  def rethrowDeliveryFailure(): Unit = synchronized { callbackFailure.foreach(throw _) }

  def requireDelivery(): Unit = synchronized {
    callbackFailure.foreach { cause =>
      if (NonFatal(cause)) throw cause
      else throw new HostDeliveryFailure(cause)
    }
  }

  def complete(result: TestResult): Unit = {
    val status = result.status match {
      case TestStatus.Succeeded => Status.Success
      case TestStatus.Failed => Status.Failure
      case TestStatus.Cancelled => Status.Canceled
      case TestStatus.Skipped => Status.Skipped
    }
    deliver(project(new TestSelector(result.id.path.mkString(" ")), status, result.failure, TimeUnit.NANOSECONDS.toMillis(result.durationNanos)))
  }

  def fail(failure: Failure): Unit = deliver(project(new SuiteSelector, Status.Error, Some(failure), 0L))

  private def project(selectorValue: Selector, statusValue: Status, failure: Option[Failure], durationValue: Long): Event = new Event {
    private val projectedThrowable = failure match {
      case Some(value) => new OptionalThrowable(ProjectedFailure.root(value))
      case None => new OptionalThrowable
    }
    override def fullyQualifiedName(): String = definition.fullyQualifiedName()
    override def fingerprint(): Fingerprint = definition.fingerprint()
    override def selector(): Selector = selectorValue
    override def status(): Status = statusValue
    override def throwable(): OptionalThrowable = projectedThrowable
    override def duration(): Long = durationValue
  }

  private def deliver(event: Event): Unit = synchronized {
    require(!closed, "Event emitted after suite task completion")
    handler match {
      case Some(value) =>
        try value.handle(event)
        catch {
          case cause: Throwable if NonFatal(cause) || cause.isInstanceOf[LinkageError] || cause.isInstanceOf[InterruptedException] =>
            callbackFailure = Some(cause)
            handler = None
            if (cause.isInstanceOf[InterruptedException]) { val _ = Thread.interrupted() }
        }
      case None if callbackFailure.nonEmpty => ()
      case None => buffered :+= event
    }
  }
}

private[bootstrap] final class HostDeliveryFailure(cause: Throwable) extends RuntimeException("Host event delivery failed", cause)

private[bootstrap] final class TaskInterruption {
  private var failure = Option.empty[InterruptedException]

  def await[A](operation: => A, cancel: () => Unit): A = {
    var completed = Option.empty[A]
    while (completed.isEmpty) {
      try completed = Some(operation)
      catch {
        case cause: InterruptedException =>
          if (failure.isEmpty) failure = Some(cause)
          val _ = Thread.interrupted()
          cancel()
      }
    }
    completed.get
  }

  def rethrow(): Unit = {
    if (Thread.interrupted() && failure.isEmpty) failure = Some(new InterruptedException("Host test task was interrupted"))
    failure.foreach(throw _)
  }
}
