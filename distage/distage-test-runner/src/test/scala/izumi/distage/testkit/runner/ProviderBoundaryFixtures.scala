package izumi.distage.testkit.runner

import izumi.distage.testkit.protocol.*

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

object ProviderBoundaryFixtures {
  def run(identity: CatalogueIdentity, verify: (Boolean, String) => Unit)(implicit ec: ExecutionContext): Future[Unit] = {
    val overrides = RunOverrides(Vector.empty, Vector.empty, MemoizationOverride.Inherit)
    val request = RunRequest(identity, Selection.All, overrides)
    val descriptor = TestDescriptor(TestId(identity.target, SuiteId("BoundarySuite"), Vector("test"), None), "test", SourceLocation.Unavailable, EffectiveSettings(Vector.empty, memoization = true))
    val success = TestResult(descriptor.id, TestStatus.Succeeded, None, 0L)
    def contribution(suite: SuiteId, tests: Vector[TestDescriptor], provider: ExecutionProvider): TestSuite = new TestSuite {
      override def register(context: RegistrationContext): RegisteredSuite = RegisteredSuite(SuiteDescriptor(suite, suite.value), tests, provider)
    }
    def session(suites: Vector[TestSuite], sink: EventSink): RunSession = new RunSession(identity, suites.map(suite => () => suite), ec, sink)
    def provider(body: (Vector[TestDescriptor], RunExecutionContext) => Future[ProviderOutcome]): ExecutionProvider = FixtureSupport.provider(body)
    def report(context: RunExecutionContext, result: TestResult): Unit = {
      context.emit(ProviderEvent.TestStarted(result.id))
      context.emit(ProviderEvent.TestCompleted(result))
    }
    def checkWire(outcome: RunOutcome, sink: FixtureSupport.RecordingSink): Unit = {
      val messages: Vector[ProtocolMessage] = sink.events :+ ProtocolMessage.Completed(outcome)
      verify(messages.forall(message => ProtocolCodec.decode(ProtocolCodec.encode(message)) == Right(message)), "Rejected provider payloads must leave wire-valid terminal messages")
    }
    val throwing = new ExecutionProvider {
      override def resolve(tests: Vector[TestDescriptor], overrides: RunOverrides): Either[Failure, Vector[TestDescriptor]] = throw new IllegalStateException("resolver failure")
      override def plan(tests: Vector[TestDescriptor]): Future[ExecutionPlan] = throw new IllegalStateException("Resolver failure must prevent planning")
    }
    val throwingSink = new FixtureSupport.RecordingSink
    val throwingSession = session(Vector(contribution(descriptor.id.suite, Vector(descriptor), throwing)), throwingSink)
    verify(throwingSession.resolve(request).left.exists(_.phase == FailurePhase.Selection), "Resolver exceptions must return Selection failures")
    val invalidRunSink = new FixtureSupport.RecordingSink
    val invalidRun = session(Vector(contribution(descriptor.id.suite, Vector(descriptor), provider((_, _) => throw new IllegalStateException("Invalid run identity must prevent execution")))), invalidRunSink)
    invalidRun.execute(RunId(""), request).failed.flatMap { error =>
      verify(error.isInstanceOf[IllegalArgumentException] && invalidRunSink.events.isEmpty, "Invalid run identity must fail before any event or body")
      throwingSession.execute(RunId("resolver-failure"), request)
    }.flatMap { outcome =>
      verify(!outcome.successful && outcome.failures.head.phase == FailurePhase.Selection, "Resolver exception must not escape execute synchronously")
      checkWire(outcome, throwingSink)
      val inert = provider((_, _) => Future.successful(ProviderOutcome(Vector.empty, Vector.empty, cancelled = false)))
      val wrongOwner = session(Vector(contribution(SuiteId("A"), Vector(descriptor.copy(id = descriptor.id.copy(suite = SuiteId("B")))), inert), contribution(SuiteId("B"), Vector.empty, inert)), new FixtureSupport.RecordingSink)
      verify(wrongOwner.discover().left.exists(_.phase == FailurePhase.Discovery), "Each test must belong to its own suite contribution")
      val negative = provider((_, context) => {
        context.emit(ProviderEvent.TestStarted(success.id))
        Future.successful(ProviderOutcome(Vector(success.copy(durationNanos = -1)), Vector.empty, cancelled = false))
      })
      val negativeSink = new FixtureSupport.RecordingSink
      session(Vector(contribution(descriptor.id.suite, Vector(descriptor), negative)), negativeSink).execute(RunId("negative-duration"), request).map { result =>
        verify(!result.successful && result.failures.exists(_.message.contains("negative")), "Negative terminal durations must reject")
        checkWire(result, negativeSink)
      }
    }.flatMap { _ =>
      val invalid = provider((_, context) => {
        try context.emit(ProviderEvent.TestStarted(success.id.copy(path = Vector("unselected")))) catch { case _: IllegalStateException => () }
        report(context, success)
        Future.successful(ProviderOutcome(Vector(success), Vector.empty, cancelled = false))
      })
      val sink = new FixtureSupport.RecordingSink
      session(Vector(contribution(descriptor.id.suite, Vector(descriptor), invalid)), sink).execute(RunId("invalid-events"), request).map { result =>
        verify(!result.successful && result.failures.exists(_.phase == FailurePhase.Transport), "A provider cannot suppress its invalid event error")
        verify(!sink.events.exists(_.event match { case RunEvent.TestStarted(_, test) => test.path == Vector("unselected"); case _ => false }), "Unselected test events must not reach the sink")
        checkWire(result, sink)
      }
    }.flatMap { _ =>
      val second = descriptor.copy(id = descriptor.id.copy(suite = SuiteId("SecondBoundarySuite")))
      val secondResult = success.copy(id = second.id)
      def swapping(own: TestResult, other: TestResult): ExecutionProvider = provider((_, context) => {
        report(context, own)
        Future.successful(ProviderOutcome(Vector(other), Vector.empty, cancelled = false))
      })
      val sink = new FixtureSupport.RecordingSink
      val swapped = session(Vector(contribution(descriptor.id.suite, Vector(descriptor), swapping(success, secondResult)), contribution(second.id.suite, Vector(second), swapping(secondResult, success))), sink)
      swapped.execute(RunId("swapped-provider-results"), request).map { result =>
        verify(!result.successful && result.failures.exists(_.message.contains("unselected")), "Provider result ownership must be checked before aggregate reconciliation")
        checkWire(result, sink)
      }
    }.flatMap { _ =>
      val failure = RunnerFailure.message(FailurePhase.Finalization, "reported finalization failure")
      val failed = provider((_, context) => {
        report(context, success)
        context.emit(ProviderEvent.PhaseFailed(failure))
        Future.successful(ProviderOutcome(Vector(success), Vector.empty, cancelled = false))
      })
      val sink = new FixtureSupport.RecordingSink
      session(Vector(contribution(descriptor.id.suite, Vector(descriptor), failed)), sink).execute(RunId("reported-failure"), request).map { result =>
        verify(!result.successful && result.failures.contains(failure), "Reported phase failures must survive an inconsistent provider summary")
        verify(sink.events.count(_.event == RunEvent.PhaseFailed(result.run, failure)) == 1, "A reported phase failure must not be replayed at completion")
        checkWire(result, sink)
      }
    }.flatMap { _ =>
      val callbackFailure = new IllegalStateException("event was recorded before delivery failed")
      var messages = Vector.empty[ProtocolMessage.Event]
      val sink = new EventSink {
        override def accept(event: ProtocolMessage.Event): Unit = synchronized {
          messages :+= event
          event.event match {
            case _: RunEvent.TestStarted => throw callbackFailure
            case _ => ()
          }
        }
      }
      val draining = provider((_, context) => {
        val failures = try {
          context.emit(ProviderEvent.TestStarted(success.id))
          Vector.empty[Failure]
        } catch { case NonFatal(cause) => Vector(RunnerFailure.fromThrowable(FailurePhase.Transport, cause)) }
        context.emit(ProviderEvent.TestCompleted(success))
        Future.successful(ProviderOutcome(Vector(success), failures, cancelled = false))
      })
      session(Vector(contribution(descriptor.id.suite, Vector(descriptor), draining)), sink).execute(RunId("partial-event-delivery"), request).map { outcome =>
        verify(!outcome.successful && outcome.failures.exists(failure => failure.phase == FailurePhase.Transport && failure.message == callbackFailure.getMessage), "A partially delivered callback failure must remain observable")
        verify(messages.map(_.sequence) == messages.indices.map(_.toLong).toVector, "A recorded event that throws must consume its ordinal before the next event")
        verify(messages.last.event == RunEvent.Finished(outcome.run, outcome) && outcome.results == Vector(success), "Draining a callback failure must preserve terminal results and completion")
      }
    }.flatMap { _ =>
      val failure = RunnerFailure.message(FailurePhase.Finalization, "repeated finalization failure")
      val repeated = provider((_, context) => {
        report(context, success)
        context.emit(ProviderEvent.PhaseFailed(failure))
        context.emit(ProviderEvent.PhaseFailed(failure))
        Future.successful(ProviderOutcome(Vector(success), Vector(failure, failure), cancelled = false))
      })
      val sink = new FixtureSupport.RecordingSink
      session(Vector(contribution(descriptor.id.suite, Vector(descriptor), repeated)), sink).execute(RunId("repeated-phase-failure"), request).flatMap { result =>
        verify(!result.successful && result.failures.count(_ == failure) == 2, "Structurally equal phase failure occurrences must retain their multiplicity")
        verify(sink.events.count(_.event == RunEvent.PhaseFailed(result.run, failure)) == 2, "Every phase failure occurrence must reach the sink once")
        checkWire(result, sink)
        val returnedOnly = provider((_, context) => {
          report(context, success)
          Future.successful(ProviderOutcome(Vector(success), Vector(failure, failure), cancelled = false))
        })
        val returnedSink = new FixtureSupport.RecordingSink
        session(Vector(contribution(descriptor.id.suite, Vector(descriptor), returnedOnly)), returnedSink).execute(RunId("returned-phase-failures"), request).map { returned =>
          verify(returned.failures.count(_ == failure) == 2 && returnedSink.events.count(_.event == RunEvent.PhaseFailed(returned.run, failure)) == 2, "Returned-only failure occurrences must each reach the sink")
          checkWire(returned, returnedSink)
        }
      }
    }.flatMap { _ =>
      val resource = DependencyKeyId(0)
      val missingOperation = DependencyKeyId(1)
      val malformed = Vector(
        PlanInspection(Vector.empty, Vector.empty, Vector.empty) -> "cover the selected tests",
        PlanInspection(
          Vector(DependencyKey(resource, "resource"), DependencyKey(missingOperation, "missing operation")),
          Vector(PlanScope(PlanScopeId(Vector(0)), PlanScopeKind.Test, Vector(descriptor.id), Vector(PlanStep(resource, PlanOperation.CallProvider, Vector(missingOperation))))),
          Vector.empty,
        ) -> "no operation in its scope or ancestor",
      )
      malformed.foldLeft(Future.successful(())) { case (previous, (inspection, reason)) =>
        previous.flatMap { _ =>
          val executions = new AtomicInteger(0)
          val invalid = new FixtureSupport.Provider {
            override def plan(selected: Vector[TestDescriptor]): Future[ExecutionPlan] = Future.successful(new FixtureSupport.Plan(selected) {
              override val inspection: PlanInspection = invalidInspection
              override def execute(context: RunExecutionContext): Future[ProviderOutcome] = {
                val _ = executions.incrementAndGet()
                Future.successful(ProviderOutcome(Vector(success), Vector.empty, cancelled = false))
              }
            })
            private val invalidInspection = inspection
          }
          val sink = new FixtureSupport.RecordingSink
          val plannedSession = session(Vector(contribution(descriptor.id.suite, Vector(descriptor), invalid)), sink)
          val resolved = plannedSession.resolve(request).fold(failure => throw new IllegalStateException(failure.message), value => value)
          plannedSession.plan(resolved).flatMap { planned =>
            verify(planned.left.exists(failure => failure.phase == FailurePhase.Planning && failure.message.contains(reason)) && executions.get() == 0 && sink.events.isEmpty, "Invalid provider inspection must reject before execution or events: " + reason)
            session(Vector(contribution(descriptor.id.suite, Vector(descriptor), invalid)), sink).execute(RunId("invalid-inspection"), request).map { outcome =>
              verify(!outcome.successful && outcome.failures.exists(failure => failure.phase == FailurePhase.Planning && failure.message.contains(reason)) && executions.get() == 0, "Execution must retain the provider inspection rejection: " + reason)
              checkWire(outcome, sink)
            }
          }
        }
      }.map { _ =>
        val invalidSettings = provider((_, _) => throw new IllegalStateException("Invalid resolved settings must prevent execution"))
        val requestedAxis = AxisChoice(AxisId("mode"), AxisValue("test"))
        val invalid = session(Vector(contribution(descriptor.id.suite, Vector(descriptor), invalidSettings)), new FixtureSupport.RecordingSink)
        verify(invalid.resolve(request.copy(overrides = overrides.copy(axes = Vector(requestedAxis)))).left.exists(failure => failure.phase == FailurePhase.Selection && failure.message.contains("activation differs")), "Provider resolution must preserve explicit effective activation overrides")
      }
    }
  }
}
