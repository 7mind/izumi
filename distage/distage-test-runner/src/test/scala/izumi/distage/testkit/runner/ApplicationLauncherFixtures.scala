package izumi.distage.testkit.runner

import izumi.distage.testkit.protocol.*
import izumi.distage.testkit.runner.spec.AnyWordSpec

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{ExecutionContext, Future, Promise}

object ApplicationLauncherFixtures {
  private final val InputFailureCases = 2
  private final val FailedPlanningCases = 1
  private final case class Case(name: String, commands: Vector[ProtocolMessage], successful: Boolean, executed: Boolean, cancelled: Boolean)

  def run(make: () => FramedChannelFixtures.Channel, label: String, context: ExecutionContext, verify: (Boolean, String) => Unit): Future[Unit] = {
    implicit val ec: ExecutionContext = context
    val identity = CatalogueIdentity(BuildId("launcher-build"), BuildTargetId("launcher-target"), CatalogueId("launcher-catalogue"))
    val run = RunId("launcher-run")
    val request = RunRequest(identity, Selection.All, RunOverrides(Vector.empty, Vector.empty, MemoizationOverride.Inherit))
    val unknown = TestId(identity.target, SuiteId("unknown"), Vector("missing"), None)
    def command(operation: RequestOperation, value: RunRequest): ProtocolMessage = ProtocolMessage.Request(operation, run, value)
    val cases = Vector(
      Case("complete", Vector(ProtocolMessage.Discover(run, identity.build, identity.target), command(RequestOperation.Resolve, request), command(RequestOperation.Plan, request), command(RequestOperation.Execute, request)), true, true, false),
      Case("inspect", Vector(command(RequestOperation.Plan, request)), true, false, false),
      Case("stale-build", Vector(command(RequestOperation.Execute, request.copy(identity = identity.copy(build = BuildId("stale"))))), false, false, false),
      Case("stale-catalogue", Vector(command(RequestOperation.Execute, request.copy(identity = identity.copy(catalogue = CatalogueId("stale"))))), false, false, false),
      Case("unknown-id", Vector(command(RequestOperation.Execute, request.copy(selection = Selection.Only(Vector.empty, Vector(unknown))))), false, false, false),
      Case("cancel-only", Vector(ProtocolMessage.Cancel(run)), false, false, false),
      Case("pre-cancel", Vector(ProtocolMessage.Cancel(run), command(RequestOperation.Execute, request)), false, false, true),
    )
    cases.foldLeft(Future.unit) { (previous, test) => previous.flatMap { _ =>
      val channel = make()
      val input = new FramedProtocolOutput(channel.sink)
      test.commands.foreach(input.accept)
      channel.sink.close()
      val source = channel.source()
      val bodies = new AtomicInteger(0)
      var messages = Vector.empty[ProtocolMessage]
      val output = new ProtocolOutput {
        override def accept(message: ProtocolMessage): Unit = synchronized {
          require(ProtocolCodec.decode(ProtocolCodec.encode(message)) == Right(message), "Launcher output must round-trip")
          messages :+= message
        }
      }
      val factory: () => TestSuite = () => new AnyWordSpec { "launcher body" in { val _ = bodies.incrementAndGet() } }
      ApplicationLauncher.run(identity, Vector(factory), context, source, output).map { result =>
        val snapshot = output.synchronized(messages)
        verify(result.successful == test.successful && bodies.get() == (if (test.executed) 1 else 0), label + " " + test.name + " has the expected result and body count")
        verify(result.outcome.exists(_.cancelled) == test.cancelled && result.outcome.isDefined == (test.executed || test.cancelled), label + " " + test.name + " retains only an actual execution outcome")
        verify(snapshot.collect { case ProtocolMessage.Completed(outcome) => outcome } == result.outcome.toVector, label + " " + test.name + " agrees with terminal output")
        if (Set("stale-build", "stale-catalogue", "unknown-id").contains(test.name)) {
          verify(snapshot.last.isInstanceOf[ProtocolMessage.Rejected] && !snapshot.exists(_.isInstanceOf[ProtocolMessage.Event]), label + " " + test.name + " rejects before execution events")
        }
      }.transform { result => source.close(); channel.close(); result }
    } }.flatMap { _ =>
      val channel = make()
      channel.sink.close()
      val source = channel.source()
      val output = new ProtocolOutput { override def accept(message: ProtocolMessage): Unit = throw new IllegalStateException("Empty launcher input must not emit: " + message) }
      ApplicationLauncher.run(identity, Vector.empty, context, source, output).failed.map { cause =>
        verify(cause.getMessage.contains("must contain a command"), label + " empty input is an error rather than successful EOF")
      }.transform { result => source.close(); channel.close(); result }
    }.flatMap(_ => inputFailure(make, label, identity, request, verify)).flatMap(_ => failedPlanning(make, label, identity, request, context, verify)).map { _ => println("APPLICATION_LAUNCHER_CONTRACTS_OK adapter=" + label + " cases=" + (cases.size + InputFailureCases + FailedPlanningCases) + " saved=revalidated cancellation=before_body empty=rejected input=drained planning=failed") }
  }

  private def failedPlanning(make: () => FramedChannelFixtures.Channel, label: String, identity: CatalogueIdentity, request: RunRequest, context: ExecutionContext, verify: (Boolean, String) => Unit): Future[Unit] = {
    implicit val ec: ExecutionContext = context
    val run = RunId("launcher-failed-plan")
    val failure = RunnerFailure.message(FailurePhase.Planning, "Controlled failed launcher plan")
    val registrations = new AtomicInteger(0)
    val executions = new AtomicInteger(0)
    val suite = new TestSuite {
      override def register(registration: RegistrationContext): RegisteredSuite = {
        val _ = registrations.incrementAndGet()
        val descriptor = SuiteDescriptor(SuiteId("FailedLauncherPlan"), "FailedLauncherPlan")
        val test = TestDescriptor(TestId(registration.target, descriptor.id, Vector("body"), None), "body", SourceLocation.Unavailable, EffectiveSettings(Vector.empty, memoization = true))
        val provider = new FixtureSupport.Provider {
          override def plan(selected: Vector[TestDescriptor]): Future[ExecutionPlan] = Future.successful(new FixtureSupport.Plan(selected) {
            override val inspection: PlanInspection = PlanInspection(Vector.empty, Vector.empty, Vector(PlanFailure(tests.map(_.id), failure)))
            override def execute(context: RunExecutionContext): Future[ProviderOutcome] = { val _ = (context, executions.incrementAndGet()); throw new IllegalStateException("Failed plan inspection must not execute") }
          })
        }
        RegisteredSuite(descriptor, Vector(test), provider)
      }
    }
    val channel = make()
    new FramedProtocolOutput(channel.sink).accept(ProtocolMessage.Request(RequestOperation.Plan, run, request))
    channel.sink.close()
    val source = channel.source()
    var messages = Vector.empty[ProtocolMessage]
    val output = new ProtocolOutput { override def accept(message: ProtocolMessage): Unit = synchronized {
      require(ProtocolCodec.decode(ProtocolCodec.encode(message)) == Right(message), "Failed plan must round-trip")
      messages :+= message
    } }
    ApplicationLauncher.run(identity, Vector(() => suite), context, source, output).map { result =>
      val snapshot = output.synchronized(messages)
      val plans = snapshot.collect { case ProtocolMessage.Planned(`run`, value) => value }
      verify(!result.successful && result.outcome.isEmpty, label + " failed plan inspection is unsuccessful without an execution outcome")
      verify(plans.size == 1 && plans.head.inspection.failures.map(_.failure) == Vector(failure), label + " failed plan inspection preserves its planning diagnostic")
      verify(registrations.get() == 1 && executions.get() == 0, label + " failed plan inspection registers once without execution")
      verify(snapshot.size == 1 && plans.head.inspection.failures.head.tests == plans.head.selection.tests.map(_.id), label + " failed plan output correlates all selected tests without execution events")
    }.transform { result => source.close(); channel.close(); result }
  }
  private def inputFailure(make: () => FramedChannelFixtures.Channel, label: String, identity: CatalogueIdentity, request: RunRequest, verify: (Boolean, String) => Unit): Future[Unit] = {
    implicit val inline: ExecutionContext = FixtureSupport.inlineContext()
    val entered = Promise[Unit]()
    val cancelled = Promise[Unit]()
    val release = Promise[Unit]()
    val released = new AtomicInteger(0)
    val original = new IllegalArgumentException("Controlled launcher input failure")
    val run = RunId("launcher-input-failure")
    val provider = FixtureSupport.provider { (plannedTests, context) =>
      val registration = context.cancellation.onRequest(() => { val _ = cancelled.trySuccess(()); Future.unit })
      plannedTests.foreach(test => context.emit(ProviderEvent.TestStarted(test.id)))
      val _ = entered.trySuccess(())
      release.future.flatMap { _ =>
        val _ = released.incrementAndGet()
        val results = plannedTests.map(test => TestResult(test.id, TestStatus.Cancelled, None, 0L))
        results.foreach(result => context.emit(ProviderEvent.TestCompleted(result)))
        registration.close().map(_ => ProviderOutcome(results, Vector.empty, context.cancellation.isRequested))
      }
    }
    val suite = new TestSuite {
      override def register(context: RegistrationContext): RegisteredSuite = {
        val suite = SuiteDescriptor(SuiteId("LauncherInputFailure"), "LauncherInputFailure")
        val test = TestDescriptor(TestId(context.target, suite.id, Vector("held"), None), "held", SourceLocation.Unavailable, EffectiveSettings(Vector.empty, memoization = true))
        RegisteredSuite(suite, Vector(test), provider)
      }
    }
    val channel = make()
    val input = new FramedProtocolOutput(channel.sink)
    input.accept(ProtocolMessage.Request(RequestOperation.Execute, run, request))
    channel.sink.close()
    val delegate = channel.source()
    val source = new ProtocolFrameSource {
      private var first = true
      override def readFrame(): Option[String] = {
        if (first) { first = false; delegate.readFrame() }
        else { require(entered.isCompleted, "Controlled provider must enter before the input failure"); throw original }
      }
      override def close(): Unit = delegate.close()
    }
    var messages = Vector.empty[ProtocolMessage]
    val output = new ProtocolOutput { override def accept(message: ProtocolMessage): Unit = { messages :+= message } }
    val execution = ApplicationLauncher.run(identity, Vector(() => suite), inline, source, output)
    try {
      verify(cancelled.isCompleted, label + " input failure requests cancellation")
      verify(!execution.isCompleted && released.get() == 0 && !messages.exists(_.isInstanceOf[ProtocolMessage.Completed]), label + " input failure waits for held finalization")
      val _ = release.trySuccess(())
      execution.failed.map { cause =>
        verify(cause eq original, label + " input failure retains its original cause")
        verify(released.get() == 1, label + " input failure drains finalization exactly once")
        verify(messages.lastOption.exists { case ProtocolMessage.Completed(outcome) => outcome.cancelled && !outcome.successful; case _ => false }, label + " input failure retains a cancelled terminal outcome")
      }.transform { result => source.close(); channel.close(); result }
    } catch {
      case scala.util.control.NonFatal(cause) =>
        val _ = release.trySuccess(())
        execution.transformWith { _ => source.close(); channel.close(); Future.failed(cause) }
    }
  }
}
