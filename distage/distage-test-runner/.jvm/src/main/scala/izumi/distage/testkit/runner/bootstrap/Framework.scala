package izumi.distage.testkit.runner.bootstrap

import izumi.distage.testkit.protocol.*
import izumi.distage.testkit.runner.*

import sbt.testing.{Event, EventHandler, Fingerprint, Framework as SbtFramework, Logger, OptionalThrowable, Runner, Selector, Status, SubclassFingerprint, SuiteSelector, Task, TaskDef, TestSelector}

import java.util.UUID
import java.util.concurrent.{Executors, TimeUnit}
import scala.concurrent.{Await, ExecutionContext, Promise}
import scala.concurrent.duration.Duration
import scala.util.control.NonFatal

final class Framework extends SbtFramework {
  private val fingerprint = new SubclassFingerprint {
    override def isModule(): Boolean = false
    override def superclassName(): String = classOf[TestSuite].getName
    override def requireNoArgConstructor(): Boolean = true
  }

  override def name(): String = "distage"
  override def fingerprints(): Array[Fingerprint] = Array(fingerprint)
  override def runner(args: Array[String], remoteArgs: Array[String], testClassLoader: ClassLoader): Runner = {
    new BootstrapRunner(args.clone(), remoteArgs.clone(), testClassLoader, BootstrapArguments.parse(args))
  }
}

private[bootstrap] object BootstrapArguments {
  def parse(arguments: Array[String]): CatalogueIdentity = {
    val pairs = arguments.toVector.grouped(2).toVector
    require(pairs.forall(_.size == 2), "Each bootstrap option requires one value")
    val expected = Set("--build-id", "--target-id", "--catalogue-id")
    require(pairs.map(_.head).toSet == expected && pairs.size == expected.size, "Bootstrap requires exactly --build-id, --target-id and --catalogue-id")
    require(pairs.forall(_(1).nonEmpty), "Bootstrap identities must not be empty")
    def value(option: String): String = pairs.find(_.head == option).get(1)
    CatalogueIdentity(BuildId(value("--build-id")), BuildTargetId(value("--target-id")), CatalogueId(value("--catalogue-id")))
  }
}

private[bootstrap] final class BootstrapRunner(
  arguments: Array[String],
  remoteArguments: Array[String],
  loader: ClassLoader,
  identity: CatalogueIdentity,
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
    val invocation = new Invocation(identity, definitions.toVector, loader)
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
            interruption.restore()
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

private[bootstrap] final class Invocation(identity: CatalogueIdentity, definitions: Vector[TaskDef], loader: ClassLoader) {
  private final val ShutdownPollSeconds = 1L
  val projections: Vector[SuiteProjection] = definitions.map(new SuiteProjection(_))
  private val completion = Promise[Either[Throwable, RunOutcome]]()
  private val cancellation = new Cancellation
  private var started = false
  @volatile private var activeSession = Option.empty[RunSession]

  def result(interruption: TaskInterruption): Either[Throwable, RunOutcome] = {
    val launch = synchronized {
      if (started) false else { started = true; true }
    }
    if (launch) { val _ = completion.success(execute(interruption)) }
    interruption.await(Await.result(completion.future, Duration.Inf), () => cancel()).map { outcome =>
      val deliveryFailures = projections.flatMap(_.deliveryFailure).map(RunnerFailure.fromThrowable(FailurePhase.Transport, _))
      outcome.copy(failures = outcome.failures ++ deliveryFailures, cancelled = outcome.cancelled || cancellation.isRequested)
    }
  }

  private def cancel(): Unit = {
    cancellation.request()
    activeSession.foreach(_.cancel())
  }

  private def execute(interruption: TaskInterruption): Either[Throwable, RunOutcome] = {
    val executor = Executors.newWorkStealingPool()
    val executionContext = ExecutionContext.fromExecutorService(executor)
    try {
      val factories = projections.map { projection => () => new TestSuite {
        override def register(context: RegistrationContext): RegisteredSuite = {
          val name = projection.definition.fullyQualifiedName()
          val instance = try classOf[TestSuite].cast(Class.forName(name, true, loader).getConstructor().newInstance())
          catch { case cause: LinkageError => throw new SuiteLoadingFailure(name, cause) }
          val registered = instance.register(context)
          projection.associate(registered.descriptor.id)
          registered
        }
      }}
      val sink = new EventSink {
        override def accept(event: ProtocolMessage.Event): Unit = event.event match {
          case RunEvent.TestCompleted(_, test) =>
            val matching = projections.filter(_.owns(test.id.suite))
            require(matching.size == 1, "Test completion has no unique suite task owner")
            matching.head.complete(test)
          case _ => ()
        }
      }
      val session = new RunSession(identity, factories, executionContext, sink)
      activeSession = Some(session)
      if (cancellation.isRequested) session.cancel()
      val request = RunRequest(identity, Selection.All, RunOverrides(Vector.empty, Vector.empty, MemoizationOverride.Inherit))
      val execution = session.execute(RunId(UUID.randomUUID().toString), request)
      val outcome = interruption.await(Await.result(execution, Duration.Inf), () => cancel())
      Right(outcome)
    } catch { case NonFatal(cause) => Left(cause) }
    finally {
      executionContext.shutdown()
      interruption.await({ while (!executionContext.awaitTermination(ShutdownPollSeconds, TimeUnit.SECONDS)) (); () }, () => cancel())
      activeSession = None
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
      case Some(value) => new OptionalThrowable(new ProjectedFailure(value))
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

private[bootstrap] final class ProjectedFailure(val failure: Failure)
  extends RuntimeException(s"${failure.phase}: ${failure.exceptionClass}: ${failure.message}", failure.causes.headOption.map(new ProjectedFailure(_)).orNull) {
  failure.causes.drop(1).foreach(cause => addSuppressed(new ProjectedFailure(cause)))
}

private[bootstrap] final class SuiteLoadingFailure(name: String, cause: LinkageError) extends RuntimeException(s"Cannot load suite $name", cause)

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

  def rethrow(): Unit = failure.foreach(throw _)
  def restore(): Unit = if (failure.nonEmpty) Thread.currentThread().interrupt()
}
