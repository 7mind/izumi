package izumi.distage.testkit.runner

import izumi.distage.testkit.protocol.*

import scala.concurrent.{ExecutionContext, Future}

private[runner] object FixtureSupport {
  abstract class Provider extends ExecutionProvider {
    override def resolve(tests: Vector[TestDescriptor], overrides: RunOverrides): Either[Failure, Vector[TestDescriptor]] = Right(tests)
  }

  abstract class Plan(override val tests: Vector[TestDescriptor]) extends ExecutionPlan {
    override val inspection: PlanInspection = PlanInspection.individualTests(tests.map(_.id))
  }

  def provider(body: (Vector[TestDescriptor], RunExecutionContext) => Future[ProviderOutcome]): ExecutionProvider = new Provider {
    override def plan(selected: Vector[TestDescriptor]): Future[ExecutionPlan] = Future.successful(new Plan(selected) {
      override def execute(context: RunExecutionContext): Future[ProviderOutcome] = body(selected, context)
    })
  }

  final class RecordingSink extends EventSink {
    private var recorded = Vector.empty[ProtocolMessage.Event]
    override def accept(event: ProtocolMessage.Event): Unit = synchronized { recorded :+= event }
    def events: Vector[ProtocolMessage.Event] = synchronized(recorded)
    def completions: Vector[TestResult] = events.collect { case ProtocolMessage.Event(_, RunEvent.TestCompleted(_, result)) => result }
  }

  final class RecordingOutput extends ProtocolOutput {
    private var recorded = Vector.empty[ProtocolMessage]
    override def accept(message: ProtocolMessage): Unit = synchronized {
      require(ProtocolCodec.decode(ProtocolCodec.encode(message)) == Right(message), "Fixture output must round-trip")
      recorded :+= message
    }
    def messages: Vector[ProtocolMessage] = synchronized(recorded)
  }

  def inlineContext(): ExecutionContext = new ExecutionContext {
    override def execute(task: Runnable): Unit = task.run()
    override def reportFailure(cause: Throwable): Unit = throw cause
  }

  def silentSink(): EventSink = new EventSink { override def accept(event: ProtocolMessage.Event): Unit = () }

  def suite(name: String, names: Vector[String])(makeProvider: Vector[TestDescriptor] => ExecutionProvider): TestSuite = new TestSuite {
    override def register(context: RegistrationContext): RegisteredSuite = {
      val descriptor = SuiteDescriptor(SuiteId(name), name)
      val tests = names.map(text => TestDescriptor(TestId(context.target, descriptor.id, Vector(text), None), text, SourceLocation.Unavailable, EffectiveSettings(Vector.empty, memoization = true)))
      RegisteredSuite(descriptor, tests, makeProvider(tests))
    }
  }
}
