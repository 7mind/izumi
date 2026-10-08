package izumi.distage.testkit.runner

import izumi.distage.testkit.protocol.*
import izumi.distage.testkit.runner.spec.AnyWordSpec

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.control.NonFatal

object ApplicationFixtures {
  def run(identity: CatalogueIdentity, context: ExecutionContext, verify: (Boolean, String) => Unit): Future[Unit] = {
    implicit val ec: ExecutionContext = context
    val constructions = new AtomicInteger(0)
    val bodies = new AtomicInteger(0)
    final class Plain extends AnyWordSpec {
      constructions.incrementAndGet()
      "a b" should { "c" in { val _ = bodies.incrementAndGet(); () } }
      "a" should { "b c" in { val _ = bodies.incrementAndGet(); () } }
    }
    val output = new FixtureSupport.RecordingOutput
    val run = RunId("application-plain")
    val application = new TestApplication(run, identity, Vector(() => new Plain), context, output)
    val inherited = RunOverrides(Vector.empty, Vector.empty, MemoizationOverride.Inherit)
    def command(operation: RequestOperation, request: RunRequest): Future[Unit] = application.accept(ProtocolMessage.Request(operation, run, request))
    def rejection(phase: FailurePhase, reason: String): Unit = verify(output.messages.last match {
      case ProtocolMessage.Rejected(_, failure) => failure.phase == phase && failure.message.contains(reason)
      case _ => false
    }, "Application rejection must retain phase and reason: " + reason)
    verify(constructions.get() == 0 && bodies.get() == 0, "Application construction suspends registration and bodies")
    application.accept(ProtocolMessage.Discover(run, BuildId("other"), identity.target)).flatMap { _ =>
      rejection(FailurePhase.Discovery, "another build target")
      verify(constructions.get() == 0, "Wrong discovery identity rejects before registration")
      application.accept(ProtocolMessage.Discover(run, identity.build, identity.target))
    }.flatMap { _ =>
      val catalogue = output.messages.last.asInstanceOf[ProtocolMessage.Discovered].catalogue
      verify(constructions.get() == 1 && bodies.get() == 0, "Discovery registers once without body execution")
      verify(catalogue.tests.map(_.id.path) == Vector(Vector("a b", "should", "c"), Vector("a", "should", "b c")), "Application keeps structured logical paths")
      val request = RunRequest(identity, Selection.Only(Vector.empty, Vector(catalogue.tests.head.id)), inherited)
      val invalid = Vector(
        request.copy(identity = identity.copy(build = BuildId("stale-build"))) -> "stale catalogue",
        request.copy(identity = identity.copy(target = BuildTargetId("stale-target"))) -> "stale catalogue",
        request.copy(identity = identity.copy(catalogue = CatalogueId("stale-catalogue"))) -> "stale catalogue",
        request.copy(selection = Selection.Only(Vector.empty, Vector(catalogue.tests.head.id.copy(path = Vector("unknown"))))) -> "Unknown explicit",
        request.copy(selection = Selection.Only(Vector.empty, Vector.empty)) -> "Explicit selection must not be empty",
        request.copy(overrides = inherited.copy(axes = Vector(AxisChoice(AxisId("unknown"), AxisValue("value"))))) -> "no activation axes",
      )
      invalid.foldLeft(Future.unit) { case (previous, (input, reason)) => previous.flatMap(_ => command(RequestOperation.Execute, input)).map { _ =>
        rejection(FailurePhase.Selection, reason)
        verify(bodies.get() == 0, "Invalid request must execute no bodies: " + reason)
      } }.flatMap(_ => application.accept(ProtocolMessage.Discover(run, identity.build, identity.target))).flatMap { _ =>
        verify(output.messages.last == ProtocolMessage.Discovered(run, catalogue) && constructions.get() == 1, "Repeated listing preserves identities and registration snapshot")
        command(RequestOperation.Resolve, request)
      }.flatMap { _ =>
        val resolved = output.messages.last.asInstanceOf[ProtocolMessage.Resolved].selection
        verify(resolved.request == request && resolved.tests.map(_.id) == Vector(catalogue.tests.head.id), "Resolve preserves the listed explicit logical identity")
        command(RequestOperation.Plan, request)
      }.flatMap { _ =>
        val prepared = output.messages.last.asInstanceOf[ProtocolMessage.Planned].plan
        verify(prepared.selection.tests.map(_.id) == Vector(catalogue.tests.head.id) && bodies.get() == 0, "Plan preserves selected identities without execution")
        command(RequestOperation.Plan, request).flatMap { _ =>
          verify(output.messages.last == ProtocolMessage.Planned(run, prepared), "Repeated plan returns the same prepared description")
          command(RequestOperation.Resolve, request.copy(selection = Selection.All))
        }.flatMap { _ =>
          rejection(FailurePhase.Selection, "Cannot change the request")
          command(RequestOperation.Execute, request)
        }.flatMap { _ =>
          val outcome = output.messages.last.asInstanceOf[ProtocolMessage.Completed].outcome
          verify(outcome.successful && outcome.results.map(_.id) == prepared.selection.tests.map(_.id) && bodies.get() == 1, "List, resolve, plan and execution agree on selected IDs")
          val events = output.messages.collect { case message: ProtocolMessage.Event => message }
          verify(events.map(_.sequence) == events.indices.map(_.toLong).toVector && events.last.event == RunEvent.Finished(run, outcome), "Application streams contiguous correlated events before Completed")
          command(RequestOperation.Execute, request)
        }.flatMap { _ =>
          rejection(FailurePhase.Selection, "execution has already started")
          verify(bodies.get() == 1, "Spent application cannot repeat execution")
          application.accept(ProtocolMessage.Cancel(run))
        }.map { _ => rejection(FailurePhase.Transport, "execution has already completed") }
      }
    }.flatMap(_ => planningFailure(identity, context, verify))
      .flatMap(_ => reentrantCommands(identity, verify))
      .flatMap(_ => heldFinalization(identity, context, verify, failChannel = false, failOracle = false))
      .flatMap(_ => heldFinalization(identity, context, verify, failChannel = true, failOracle = false))
      .flatMap(_ => heldFinalization(identity, context, verify, failChannel = false, failOracle = true))
      .flatMap(_ => terminalCancellation(identity, context, verify))
      .flatMap(_ => channelFailure(identity, context, verify))
      .map(_ => println("APPLICATION_FIXTURES_OK selection=validated plan=reused cancellation=immediate finalization=awaited delivery=retained"))
  }

  private def suite(provider: ExecutionProvider, name: String): TestSuite = FixtureSupport.suite(name, Vector("body"))(_ => provider)

  private def reentrantCommands(identity: CatalogueIdentity, verify: (Boolean, String) => Unit): Future[Unit] = {
    implicit val inline: ExecutionContext = FixtureSupport.inlineContext()
    val plans = new AtomicInteger(0)
    val executions = new AtomicInteger(0)
    val gate = Promise[ExecutionPlan]()
    var prepared = Option.empty[ExecutionPlan]
    val provider = new FixtureSupport.Provider {
      override def plan(selected: Vector[TestDescriptor]): Future[ExecutionPlan] = {
        plans.incrementAndGet()
        prepared = Some(new FixtureSupport.Plan(selected) {
          override def execute(context: RunExecutionContext): Future[ProviderOutcome] = {
            executions.incrementAndGet()
            val results = FixtureSupport.succeed(tests, context)
            Future.successful(ProviderOutcome(results, Vector.empty, cancelled = false))
          }
        })
        gate.future
      }
    }
    val run = RunId("reentrant-application")
    val request = RunRequest(identity, Selection.All, RunOverrides(Vector.empty, Vector.empty, MemoizationOverride.Inherit))
    var application = Option.empty[TestApplication]
    var pendingPlan = Option.empty[Future[Unit]]
    val output = new FixtureSupport.RecordingOutput
    val reentrant = new ProtocolOutput {
      override def accept(message: ProtocolMessage): Unit = {
        output.accept(message)
        message match {
          case _: ProtocolMessage.Discovered => pendingPlan = Some(application.getOrElse(throw new IllegalStateException("Application is not attached")).accept(ProtocolMessage.Request(RequestOperation.Plan, run, request)))
          case _ => ()
        }
      }
    }
    val created = new TestApplication(run, identity, Vector(() => suite(provider, "ReentrantSuite")), inline, reentrant)
    application = Some(created)
    val discovery = created.accept(ProtocolMessage.Discover(run, identity.build, identity.target))
    val inspection = pendingPlan.getOrElse(throw new IllegalStateException("Discovery did not submit its reentrant plan"))
    val execution = created.accept(ProtocolMessage.Request(RequestOperation.Execute, run, request))
    try {
      verify(discovery.isCompleted && !inspection.isCompleted && !execution.isCompleted, "Reentrant inline output must keep execution queued behind its held plan")
      verify(plans.get() == 1 && executions.get() == 0, "Reentrant inline commands must not start a second planning attempt")
    } finally { val _ = gate.trySuccess(prepared.getOrElse(throw new IllegalStateException("Provider did not prepare a plan"))) }
    Future.sequence(Vector(discovery, inspection, execution)).map { _ =>
      verify(plans.get() == 1 && executions.get() == 1 && output.messages.last.asInstanceOf[ProtocolMessage.Completed].outcome.successful, "Inline reentrant execution uses its original prepared plan")
      verify(output.messages.indexWhere(_.isInstanceOf[ProtocolMessage.Planned]) < output.messages.indexWhere(_.isInstanceOf[ProtocolMessage.Completed]), "Reentrant inspection completes before execution output")
    }
  }

  private def planningFailure(identity: CatalogueIdentity, context: ExecutionContext, verify: (Boolean, String) => Unit): Future[Unit] = {
    implicit val ec: ExecutionContext = context
    val plans = new AtomicInteger(0)
    val resolutions = new AtomicInteger(0)
    val cause = new IllegalStateException("planning extension failed")
    val provider = new ExecutionProvider {
      override def resolve(tests: Vector[TestDescriptor], overrides: RunOverrides): Either[Failure, Vector[TestDescriptor]] = { val _ = (overrides, resolutions.incrementAndGet()); Right(tests) }
      override def plan(tests: Vector[TestDescriptor]): Future[ExecutionPlan] = { val _ = (tests, plans.incrementAndGet()); throw cause }
    }
    val output = new FixtureSupport.RecordingOutput
    val run = RunId("application-planning-failure")
    val application = new TestApplication(run, identity, Vector(() => suite(provider, "PlanningFailure")), context, output)
    val request = RunRequest(identity, Selection.All, RunOverrides(Vector.empty, Vector.empty, MemoizationOverride.Inherit))
    val operations = Vector(RequestOperation.Plan, RequestOperation.Plan, RequestOperation.Execute)
    operations.foldLeft(Future.unit) { (previous, operation) => previous.flatMap(_ => application.accept(ProtocolMessage.Request(operation, run, request))) }.map { _ =>
      val failures = output.messages.collect { case ProtocolMessage.Rejected(_, failure) => failure }
      verify(plans.get() == 1 && resolutions.get() == 1 && failures.size == operations.size, "Application retains a failed planning attempt without retrying the spent session")
      verify(failures.distinct.size == 1 && failures.head.phase == FailurePhase.Planning && failures.head.message == cause.getMessage, "Original planning extension failure remains separate from test failure")
      verify(!output.messages.exists(_.isInstanceOf[ProtocolMessage.Completed]), "Rejected planning cannot fabricate successful completion")
    }
  }

  private def heldFinalization(identity: CatalogueIdentity, context: ExecutionContext, verify: (Boolean, String) => Unit, failChannel: Boolean, failOracle: Boolean): Future[Unit] = {
    implicit val ec: ExecutionContext = context
    val entered = Promise[Unit]()
    val cancelled = Promise[Unit]()
    val release = Promise[Unit]()
    val plans = new AtomicInteger(0)
    val acquired = new AtomicInteger(0)
    val released = new AtomicInteger(0)
    val writesAfterFailure = new AtomicInteger(0)
    val output = new FixtureSupport.RecordingOutput
    val deliveryError = new IllegalStateException("event channel failed")
    val oracleError = new IllegalStateException("deliberately failing held-state oracle")
    val sink = new ProtocolOutput {
      override def accept(message: ProtocolMessage): Unit = message match {
        case ProtocolMessage.Event(_, _: RunEvent.TestCompleted) if failChannel => val _ = writesAfterFailure.incrementAndGet(); throw deliveryError
        case _ =>
          verify(writesAfterFailure.get() == 0, "A failed event channel must receive no later writes")
          output.accept(message)
      }
    }
    val provider = new FixtureSupport.Provider {
      override def plan(selected: Vector[TestDescriptor]): Future[ExecutionPlan] = {
        plans.incrementAndGet()
        Future.successful(new FixtureSupport.Plan(selected) {
          override def execute(execution: RunExecutionContext): Future[ProviderOutcome] = {
            acquired.incrementAndGet()
            val registration = execution.cancellation.onRequest(() => { val _ = cancelled.trySuccess(()); Future.unit })
            val results = FixtureSupport.succeed(tests, execution)
            entered.success(())
            release.future.flatMap(_ => registration.close()).map { _ =>
              released.incrementAndGet()
              ProviderOutcome(results, Vector.empty, execution.cancellation.isRequested)
            }
          }
        })
      }
    }
    val run = RunId(if (failChannel) "application-channel-cleanup" else "application-cancellation")
    val application = new TestApplication(run, identity, Vector(() => suite(provider, "HeldFinalization")), context, sink)
    val request = RunRequest(identity, Selection.All, RunOverrides(Vector.empty, Vector.empty, MemoizationOverride.Inherit))
    val plan = ProtocolMessage.Request(RequestOperation.Plan, run, request)
    application.accept(plan).flatMap(_ => application.accept(plan)).flatMap { _ =>
      verify(plans.get() == 1 && acquired.get() == 0, "Repeated inspection reuses one plan and acquires no resources")
      val execution = application.accept(ProtocolMessage.Request(RequestOperation.Execute, run, request))
      val assertions = entered.future.flatMap { _ =>
        if (failOracle) throw oracleError
        verify(!execution.isCompleted && acquired.get() == 1 && released.get() == 0, "Application command waits for held resource finalization")
        if (failChannel) cancelled.future
        else application.accept(ProtocolMessage.Cancel(run)).flatMap(_ => cancelled.future)
      }.flatMap { _ =>
        verify(!execution.isCompleted && !output.messages.exists(_.isInstanceOf[ProtocolMessage.Completed]), "Cancellation bypasses command queue without completing held cleanup")
        release.success(())
        if (failChannel) execution.failed.map { failure =>
          verify((failure eq deliveryError) && released.get() == 1 && writesAfterFailure.get() == 1, "Delivery failure retains original cause and awaits one resource release")
          verify(!output.messages.exists { case ProtocolMessage.Event(_, _: RunEvent.Finished) => true; case _: ProtocolMessage.Completed => true; case _ => false }, "Failed channel cannot publish later terminal success")
        } else execution.map { _ =>
          val outcome = output.messages.last.asInstanceOf[ProtocolMessage.Completed].outcome
          verify(outcome.cancelled && !outcome.successful && released.get() == 1 && plans.get() == 1, "Cancellation completes only after release without replanning")
          verify(output.messages(output.messages.size - 2) match { case ProtocolMessage.Event(_, RunEvent.Finished(_, result)) => result == outcome; case _ => false }, "Finished precedes Completed after finalization")
        }
      }
      val drained = assertions.recoverWith { case NonFatal(cause) =>
        val _ = release.trySuccess(())
        execution.transform { result =>
          result.failed.toOption.filterNot(_ eq cause).foreach(cause.addSuppressed)
          scala.util.Failure(cause)
        }
      }
      if (failOracle) drained.failed.map { cause =>
        verify((cause eq oracleError) && released.get() == 1 && execution.isCompleted, "Failed held-state oracle retains its cause after provider cleanup drains")
      } else drained
    }
  }

  private def terminalCancellation(identity: CatalogueIdentity, context: ExecutionContext, verify: (Boolean, String) => Unit): Future[Unit] = {
    implicit val ec: ExecutionContext = context
    val output = new FixtureSupport.RecordingOutput
    val run = RunId("terminal-cancellation")
    var application = Option.empty[TestApplication]
    var cancellation = Option.empty[Future[Unit]]
    val sink = new ProtocolOutput {
      override def accept(message: ProtocolMessage): Unit = {
        output.accept(message)
        message match {
          case ProtocolMessage.Event(_, _: RunEvent.Finished) => cancellation = Some(application.getOrElse(throw new IllegalStateException("Application is not attached")).accept(ProtocolMessage.Cancel(run)))
          case _ => ()
        }
      }
    }
    val created = new TestApplication(run, identity, Vector(() => new AnyWordSpec { "body" in () }), context, sink)
    application = Some(created)
    val request = RunRequest(identity, Selection.All, RunOverrides(Vector.empty, Vector.empty, MemoizationOverride.Inherit))
    created.accept(ProtocolMessage.Request(RequestOperation.Execute, run, request)).flatMap { _ =>
      cancellation.getOrElse(throw new IllegalStateException("Finished did not submit its cancellation"))
    }.map { _ =>
      verify(output.messages.exists { case ProtocolMessage.Rejected(`run`, failure) => failure.phase == FailurePhase.Transport && failure.message.contains("already completed"); case _ => false }, "Finished closes cancellation admission before terminal output callbacks")
      verify(output.messages.collect { case ProtocolMessage.Completed(outcome) => outcome }.exists(outcome => outcome.successful && !outcome.cancelled), "Terminal cancellation cannot rewrite the completed outcome")
    }
  }

  private def channelFailure(identity: CatalogueIdentity, context: ExecutionContext, verify: (Boolean, String) => Unit): Future[Unit] = {
    implicit val ec: ExecutionContext = context
    val writes = new AtomicInteger(0)
    val cause = new IllegalStateException("description channel failed")
    val output = new ProtocolOutput { override def accept(message: ProtocolMessage): Unit = { val _ = (message, writes.incrementAndGet()); throw cause } }
    val application = new TestApplication(RunId("description-channel-failure"), identity, Vector.empty, context, output)
    val command = ProtocolMessage.Discover(application.run, identity.build, identity.target)
    application.accept(command).failed.flatMap { first =>
      verify(first eq cause, "Description delivery failure returns its original cause")
      application.accept(command).failed
    }.map { second => verify((second eq cause) && writes.get() == 1, "Failed channel poisons later queued commands without retries") }
  }
}
