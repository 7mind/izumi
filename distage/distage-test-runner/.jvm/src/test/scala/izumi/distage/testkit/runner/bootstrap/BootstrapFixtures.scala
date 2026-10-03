package izumi.distage.testkit.runner.bootstrap

import izumi.distage.testkit.protocol.*
import izumi.distage.testkit.runner.*
import izumi.distage.testkit.runner.spec.{AnyWordSpec, AsyncWordSpec}

import sbt.testing.{Event, EventHandler, Status, SuiteSelector, Task, TaskDef, TestSelector}

import java.util.concurrent.{Executors, TimeUnit}
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*

object BootstrapFixtures {
  private final val ParallelTasks = 2
  private final val TimeoutSeconds = 30

  def main(args: Array[String]): Unit = {
    var checks = 0
    def verify(condition: Boolean, message: String): Unit = {
      checks += 1
      if (!condition) throw new IllegalStateException(message)
    }
    def rejected(body: => Unit): Boolean = {
      try { body; false } catch { case _: IllegalArgumentException => true; case _: IllegalStateException => true }
    }
    val framework = new Framework
    val flags = Array("--build-id", "bootstrap-build", "--target-id", "bootstrap-target", "--catalogue-id", "bootstrap-catalogue")
    val loader = getClass.getClassLoader
    verify(rejected { val _ = framework.runner(Array.empty, Array.empty, loader) }, "Required catalogue arguments must not default")
    verify(rejected { val _ = framework.runner(flags ++ Array("--target-id", "duplicate"), Array.empty, loader) }, "Duplicate bootstrap options must reject")
    val runner = framework.runner(flags, Array.empty, loader)
    def definition(name: String): TaskDef = new TaskDef(name, framework.fingerprints().head, false, Array(new SuiteSelector))
    val definitions = Array(definition(classOf[BootstrapPlainSuite].getName), definition(classOf[BootstrapAsyncSuite].getName))
    verify(rejected { val _ = runner.tasks(Array(definitions.head, definitions.head)) }, "Duplicate host suite definitions must reject")
    verify(rejected { val _ = runner.tasks(Array(new TaskDef(definitions.head.fullyQualifiedName(), framework.fingerprints().head, true, Array(new TestSelector("unsupported"))))) }, "Unsupported selectors must reject explicitly")

    val previousThreads = Thread.getAllStackTraces.keySet().asScala.toSet
    val tasks = runner.tasks(definitions)
    val first = new RecordingHandler
    val second = new RecordingHandler
    execute(tasks.head, first)
    verify(first.events.size == 2 && first.events.forall(_.status() == Status.Success), "First task launches the complete selected session without waiting for other tasks")
    verify(second.events.isEmpty, "An inactive suite handler must receive no event")
    execute(tasks.last, second)
    verify(second.events.size == 2 && second.events.forall(_.status() == Status.Success), "Later suite tasks project their buffered terminal results")
    verify(first.events.forall(_.fullyQualifiedName() == definitions.head.fullyQualifiedName()) && second.events.forall(_.fullyQualifiedName() == definitions.last.fullyQualifiedName()), "Equal display names must retain suite ownership")
    verify(first.events.forall(_.duration() >= 0L), "Host event durations must be nonnegative milliseconds")
    verify(rejected { execute(tasks.head, new RecordingHandler) }, "An ordinary suite task must not execute twice")

    val repeated = runner.tasks(definitions)
    val repeatedHandlers = repeated.map { task =>
      val handler = new RecordingHandler
      execute(task, handler)
      handler
    }
    verify(repeatedHandlers.forall(handler => handler.events.size == 2 && handler.events.forall(_.status() == Status.Success)), "Repeated task groups must instantiate fresh suites and body state")
    val executor = Executors.newFixedThreadPool(ParallelTasks)
    implicit val ec: ExecutionContext = ExecutionContext.fromExecutorService(executor)
    try {
      val parallel = runner.tasks(definitions)
      val handlers = parallel.map(_ => new RecordingHandler)
      val _ = Await.result(Future.sequence(parallel.zip(handlers).toVector.map { case (task, handler) => Future { execute(task, handler) } }), TimeoutSeconds.seconds)
      verify(handlers.forall(handler => handler.events.size == 2 && handler.events.forall(_.status() == Status.Success)), "Concurrent suite tasks must share one session and serialize each handler")
    } finally {
      executor.shutdown()
      verify(executor.awaitTermination(TimeoutSeconds, TimeUnit.SECONDS), "Fixture task executor must terminate")
    }
    val failing = runner.tasks(Array(definition(classOf[BootstrapFailingSuite].getName)))
    val failureHandler = new RecordingHandler
    execute(failing.head, failureHandler)
    verify(failureHandler.events.size == 1 && failureHandler.events.head.status() == Status.Failure, "Assertion failure must remain a host failure")
    def retainsAssertion(cause: Throwable): Boolean = cause.getMessage.contains("Assertion failed") || Option(cause.getCause).exists(retainsAssertion) || cause.getSuppressed.exists(retainsAssertion)
    verify(retainsAssertion(failureHandler.events.head.throwable().get()), "Host failure must preserve assertion diagnostics in its exception tree")
    val captured = runner.tasks(Array(definition(classOf[BootstrapThrowableSuite].getName)))
    val captureHandler = new RecordingHandler
    execute(captured.head, captureHandler)
    verify(captureHandler.events.size == 4 && captureHandler.events.forall(event => event.status() == Status.Failure && event.throwable().isDefined && event.selector().isInstanceOf[TestSelector]), "Captured Throwables must retain all four host body failures")
    def captureEvent(name: String): Throwable = captureHandler.events.find(_.selector().asInstanceOf[TestSelector].testName() == name).get.throwable().get()
    Vector("Message", "Cause", "Stack").foreach { field =>
      verify(captureEvent(field.toLowerCase + " accessor").getSuppressed.exists(error => error.getMessage.contains(field) && error.getMessage.contains("java.lang.IllegalStateException")), "Host exceptions must expose explicit " + field + " capture errors")
    }
    val suppressed = captureEvent("suppressed exception")
    verify(suppressed.getSuppressed.map(_.getMessage).toVector == Vector("Test: java.lang.IllegalStateException: suppressed one", "Test: java.lang.IllegalArgumentException: suppressed two"), "Host exceptions must preserve ordered suppressed children")
    verify(suppressed.getSuppressed.head.getCause.getMessage.contains("suppressed cause"), "Host suppressed children must retain their nested causes")
    verify(suppressed.getCause.getMessage.contains("ordinary cause"), "Host causal edges must remain separate from suppressed children")
    val teardown = runner.tasks(Array(definition(classOf[BootstrapFinalizingSuite].getName)))
    val teardownHandler = new RecordingHandler
    execute(teardown.head, teardownHandler)
    verify(teardownHandler.events.map(_.status()) == Vector(Status.Success, Status.Error), "Run-level finalizer failure must prevent host success after body success")
    val missing = runner.tasks(Array(definition("izumi.fixtures.MissingSuite")))
    val missingHandler = new RecordingHandler
    execute(missing.head, missingHandler)
    verify(missingHandler.events.size == 1 && missingHandler.events.head.status() == Status.Error, "Launch/discovery failures must produce a suite error")
    val bufferedTasks = runner.tasks(definitions :+ definition(classOf[BootstrapFailingSuite].getName))
    execute(bufferedTasks.head, new RecordingHandler)
    val callbackFailure = new IllegalStateException("Buffered callback failed")
    val rejectedHandler = new EventHandler { override def handle(event: Event): Unit = throw callbackFailure }
    val propagated = try { val _ = bufferedTasks(1).execute(rejectedHandler, Array.empty); false } catch { case cause: IllegalStateException => cause eq callbackFailure }
    verify(propagated, "Buffered handler failure must propagate with its original identity")
    val laterHandler = new RecordingHandler
    execute(bufferedTasks.last, laterHandler)
    verify(laterHandler.events.map(_.status()) == Vector(Status.Failure, Status.Error), "Later suite tasks must retain an observed buffered handler failure in the group outcome")
    verify(runner.done().isEmpty, "Runner completion must release its lifecycle")
    verify(rejected { val _ = runner.tasks(definitions) } && rejected { val _ = runner.done() }, "Spent runners must reject subsequent task requests and completion")
    verify(Thread.getAllStackTraces.keySet().asScala.filterNot(previousThreads.contains).filter(_.getName.startsWith("ForkJoinPool-")).forall(!_.isAlive), "Bootstrap-owned executor threads must terminate before host completion")
    println(s"BOOTSTRAP_FIXTURES_OK checks=$checks tasks=sequential+parallel sessions=fresh handlers=bounded")
  }

  private def execute(task: Task, handler: RecordingHandler): Unit = {
    try {
      val additional = task.execute(handler, Array.empty)
      if (additional.nonEmpty) throw new IllegalStateException("Bootstrap must return ordinary terminal suite tasks")
    } finally handler.close()
  }

  private final class RecordingHandler extends EventHandler {
    private var values = Vector.empty[Event]
    private var closed = false
    private val activeCallbacks = new AtomicInteger(0)
    override def handle(event: Event): Unit = {
      val active = activeCallbacks.incrementAndGet()
      try {
        require(active == 1, "Host handler received concurrent callbacks")
        synchronized {
          require(!closed, "Host handler received a late callback")
          values :+= event
        }
      } finally { val _ = activeCallbacks.decrementAndGet() }
    }
    def events: Vector[Event] = synchronized { values }
    def close(): Unit = synchronized {
      require(activeCallbacks.get() == 0, "Suite task returned while a callback was active")
      closed = true
    }
  }
}

final class BootstrapPlainSuite extends AnyWordSpec {
  private val first = new AtomicInteger(0)
  private val second = new AtomicInteger(0)
  "equal display name" should {
    "first" in assert(first.incrementAndGet() == 1)
    "second" in assert(second.incrementAndGet() == 1)
  }
}

final class BootstrapAsyncSuite extends AsyncWordSpec {
  private val first = new AtomicInteger(0)
  private val second = new AtomicInteger(0)
  "equal display name" should {
    "first" in Future { assert(first.incrementAndGet() == 1) }
    "second" in Future { assert(second.incrementAndGet() == 1) }
  }
}

final class BootstrapFailingSuite extends AnyWordSpec {
  "assertion" should { "fail" in assert(false) }
}

final class BootstrapThrowableSuite extends AnyWordSpec {
  "message accessor" in { throw new RuntimeException("original") { override def getMessage: String = throw new IllegalStateException("message accessor") } }
  "cause accessor" in { throw new RuntimeException("original") { override def getCause: Throwable = throw new IllegalStateException("cause accessor") } }
  "stack accessor" in { throw new RuntimeException("original") { override def getStackTrace: Array[StackTraceElement] = throw new IllegalStateException("stack accessor") } }
  "suppressed exception" in {
    val original = new RuntimeException("original", new IllegalArgumentException("ordinary cause"))
    original.addSuppressed(new IllegalStateException("suppressed one", new UnsupportedOperationException("suppressed cause")))
    original.addSuppressed(new IllegalArgumentException("suppressed two"))
    throw original
  }
}

final class BootstrapFinalizingSuite extends TestSuite {
  override def register(context: RegistrationContext): RegisteredSuite = {
    val suite = SuiteDescriptor(SuiteId(getClass.getName), getClass.getSimpleName)
    val descriptor = TestDescriptor(TestId(context.target, suite.id, Vector("finalized test"), None), "finalized test", SourceLocation.Unavailable, EffectiveSettings(Vector.empty, memoization = true))
    val provider = new ExecutionProvider {
      override def resolve(tests: Vector[TestDescriptor], overrides: RunOverrides): Either[Failure, Vector[TestDescriptor]] = Right(tests)
      override def plan(selected: Vector[TestDescriptor]): Future[ExecutionPlan] = Future.successful(new ExecutionPlan {
        override val tests: Vector[TestDescriptor] = selected
        override def execute(context: RunExecutionContext): Future[ProviderOutcome] = {
          val result = TestResult(descriptor.id, TestStatus.Succeeded, None, 0L)
          context.emit(ProviderEvent.TestStarted(descriptor.id))
          context.emit(ProviderEvent.TestCompleted(result))
          Future.successful(ProviderOutcome(Vector(result), Vector(RunnerFailure.message(FailurePhase.Finalization, "finalizer failed")), cancelled = false))
        }
      })
    }
    RegisteredSuite(suite, Vector(descriptor), provider)
  }
}
