package izumi.distage.testkit.runner

import izumi.distage.testkit.protocol.*
import izumi.distage.testkit.runner.spec.{AnyWordSpec, AsyncWordSpec}

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{ExecutionContext, Future, Promise}

object BaseRunnerFixtures {
  private final class PlainSuite(instances: AtomicInteger, syncBodies: AtomicInteger, futureBodies: AtomicInteger) extends AsyncWordSpec {
    instances.incrementAndGet()
    val capturedExecutionContext: ExecutionContext = executionContext
    "outer" should {
      "nested" must { "sync" in { syncBodies.incrementAndGet(); () } }
      "future" in Future { futureBodies.incrementAndGet(); () }(using capturedExecutionContext).map(_ => ())
    }
    "after" can { "sync" in { syncBodies.incrementAndGet(); () } }
  }

  def main(args: Array[String]): Unit = FixturePlatform.run { implicit ec =>
    val checks = new AtomicInteger(0)
    def verify(condition: Boolean, message: String): Unit = {
      checks.incrementAndGet()
      if (!condition) throw new IllegalStateException(message)
    }
    val identity = CatalogueIdentity(BuildId("base-build"), BuildTargetId("base-target"), CatalogueId("base-catalogue"))
    val inherited = RunOverrides(Vector.empty, Vector.empty, MemoizationOverride.Inherit)
    val instances = new AtomicInteger(0)
    val syncBodies = new AtomicInteger(0)
    val futureBodies = new AtomicInteger(0)
    def plainSuite(): PlainSuite = new PlainSuite(instances, syncBodies, futureBodies)
    final class RecordingSink extends EventSink {
      private var events = Vector.empty[ProtocolMessage.Event]
      override def accept(event: ProtocolMessage.Event): Unit = synchronized { events :+= event }
      def snapshot: Vector[ProtocolMessage.Event] = synchronized { events }
    }
    def session(factories: Vector[() => TestSuite], sink: EventSink): RunSession = new RunSession(identity, factories, ec, sink)
    def catalogue(session: RunSession): Catalogue = session.discover().fold(failure => throw new IllegalStateException(failure.message), value => value)
    val firstSink = new RecordingSink
    val first = session(Vector(() => plainSuite()), firstSink)
    verify(instances.get() == 0, "Session construction must not instantiate suites")
    val discovered = catalogue(first)
    verify(catalogue(first) == discovered && instances.get() == 1, "Discovery registers a session's factories exactly once")
    verify(syncBodies.get() == 0 && futureBodies.get() == 0, "Discovery must not execute synchronous or Future bodies")
    verify(discovered.tests.map(_.id.path) == Vector(Vector("outer", "should", "nested", "must", "sync"), Vector("outer", "should", "future"), Vector("after", "can", "sync")), "Nested branches must restore their outer path")
    verify(discovered.tests.forall(_.location.isInstanceOf[SourceLocation.Known]), "Registration must record available compiler source positions")
    val futureId = discovered.tests(1).id
    val request = RunRequest(identity, Selection.Only(Vector.empty, Vector(futureId)), inherited)
    val unknown = request.copy(selection = Selection.Only(Vector.empty, Vector(futureId.copy(path = Vector("unknown")))))
    verify(first.resolve(unknown).left.exists(_.phase == FailurePhase.Selection), "Unknown explicit IDs fail before planning")
    verify(first.resolve(request.copy(identity = identity.copy(catalogue = CatalogueId("stale")))).isLeft, "Stale catalogue requests must reject")
    verify(first.resolve(request.copy(selection = Selection.Only(Vector.empty, Vector.empty))).isLeft, "Empty explicit selection must reject")
    verify(first.resolve(request.copy(overrides = inherited.copy(axes = Vector(AxisChoice(AxisId("unknown"), AxisValue("value")))))).isLeft, "Plain providers reject unsupported activation axes")

    first.execute(RunId("first"), request).flatMap { selected =>
      verify(selected.successful && selected.results.map(_.id) == Vector(futureId), "Only selected Future body may execute")
      verify(syncBodies.get() == 0 && futureBodies.get() == 1, "Future body completes through the session execution context")
      val events = firstSink.snapshot
      verify(events.map(_.sequence) == events.indices.map(_.toLong).toVector, "Concurrent report events must have a contiguous sequence")
      verify(events.last.event == RunEvent.Finished(selected.run, selected), "Terminal event must contain the complete outcome")
      val repeated = session(Vector(() => plainSuite()), new RecordingSink)
      verify(catalogue(repeated).tests.map(_.id) == discovered.tests.map(_.id), "Repeated sessions preserve logical IDs")
      repeated.execute(RunId("repeated"), RunRequest(identity, Selection.All, inherited))
    }.flatMap { repeated =>
      verify(repeated.successful && repeated.results.size == 3 && instances.get() == 2, "Repeated session creates and executes its own suite")
      final class IsolatedSuite extends AnyWordSpec {
        private var ownState = 0
        "session" should { "own its state" in { ownState += 1; assert(ownState == 1) } }
      }
      val left = session(Vector(() => new IsolatedSuite), new RecordingSink)
      val right = session(Vector(() => new IsolatedSuite), new RecordingSink)
      Future.sequence(Vector(left.execute(RunId("left"), RunRequest(identity, Selection.All, inherited)), right.execute(RunId("right"), RunRequest(identity, Selection.All, inherited))))
    }.flatMap { isolated =>
      verify(isolated.forall(_.successful), "Concurrent sessions must not share suite registration or body state")
      final class DuplicateSuite extends AnyWordSpec {
        "same" should { "test" in (); "test" in () }
      }
      verify(session(Vector(() => new DuplicateSuite), new RecordingSink).discover().left.exists(_.message.contains("Duplicate test")), "Duplicate logical IDs must reject discovery")
      verify(session(Vector(() => plainSuite(), () => plainSuite()), new RecordingSink).discover().left.exists(_.message.contains("Duplicate suite")), "Duplicate suite IDs must reject discovery")
      val shared = plainSuite()
      val original = session(Vector(() => shared), new RecordingSink)
      val reused = session(Vector(() => shared), new RecordingSink)
      verify(original.discover().isRight && reused.discover().isLeft, "A suite instance cannot be reused by a different session")
      final class FailingSuite extends AnyWordSpec {
        "assertion" should { "fail" in { val value = 1; assert(value == 2) } }
      }
      session(Vector(() => new FailingSuite), new RecordingSink).execute(RunId("assertion"), RunRequest(identity, Selection.All, inherited))
    }.flatMap { failed =>
      verify(!failed.successful && failed.results.head.status == TestStatus.Failed, "AssertionFailure must produce a failed result")
      def retainsAssertion(value: izumi.distage.testkit.protocol.Failure): Boolean =
        (value.exceptionClass.endsWith("AssertionFailure") && value.message.contains("Assertion failed")) || value.causes.exists(retainsAssertion)
      verify(failed.results.head.failure.exists(retainsAssertion), "Failure tree must retain the original assertion diagnostics")
      val cancelled = session(Vector(() => plainSuite()), new RecordingSink)
      cancelled.cancel()
      cancelled.execute(RunId("cancelled"), RunRequest(identity, Selection.All, inherited))
    }.flatMap { cancelled =>
      verify(!cancelled.successful && cancelled.cancelled && cancelled.results.forall(_.status == TestStatus.Cancelled), "Cancellation must produce terminal results without invoking bodies")
      val acquired = new AtomicInteger(0)
      val released = new AtomicInteger(0)
      val completedBody = Promise[Unit]()
      val releaseGate = Promise[Unit]()
      val finalizingProvider = new ExecutionProvider {
        override def resolve(tests: Vector[TestDescriptor], overrides: RunOverrides): Either[Failure, Vector[TestDescriptor]] = Right(tests)
        override def plan(selected: Vector[TestDescriptor]): Future[ExecutionPlan] = Future.successful(new ExecutionPlan {
          override val tests: Vector[TestDescriptor] = selected
          override def execute(context: RunExecutionContext): Future[ProviderOutcome] = {
            acquired.incrementAndGet()
            val results = tests.map { test =>
              context.emit(ProviderEvent.TestStarted(test.id))
              val result = TestResult(test.id, TestStatus.Succeeded, None, 0L)
              context.emit(ProviderEvent.TestCompleted(result))
              result
            }
            completedBody.success(())
            releaseGate.future.map { _ =>
              released.incrementAndGet()
              ProviderOutcome(results, Vector(RunnerFailure.message(FailurePhase.Finalization, "finalizer failed")), cancelled = false)
            }
          }
        })
      }
      def contributed(provider: ExecutionProvider, suiteName: String, names: Vector[String]): TestSuite = new TestSuite {
        override def register(context: RegistrationContext): RegisteredSuite = {
          val suite = SuiteDescriptor(SuiteId(suiteName), suiteName)
          val tests = names.map(name => TestDescriptor(TestId(context.target, suite.id, Vector(name), None), name, SourceLocation.Unavailable, EffectiveSettings(Vector.empty, memoization = true)))
          RegisteredSuite(suite, tests, provider)
        }
      }
      val finalizing = session(Vector(() => contributed(finalizingProvider, "FinalizingSuite", Vector("one", "two"))), new RecordingSink)
      val _ = catalogue(finalizing)
      verify(acquired.get() == 0 && released.get() == 0, "Discovery must not acquire provider resources")
      val outcome = finalizing.execute(RunId("finalization"), RunRequest(identity, Selection.All, inherited))
      completedBody.future.flatMap { _ =>
        verify(!outcome.isCompleted && acquired.get() == 1 && released.get() == 0, "Run completion must wait for provider finalization")
        releaseGate.success(())
        outcome
      }.flatMap { finalization =>
        verify(!finalization.successful && finalization.failures.exists(_.phase == FailurePhase.Finalization) && released.get() == 1, "Finalizer failure must be terminal and non-successful after release")
        val incompleteProvider = new ExecutionProvider {
          override def resolve(tests: Vector[TestDescriptor], overrides: RunOverrides): Either[Failure, Vector[TestDescriptor]] = Right(tests)
          override def plan(selected: Vector[TestDescriptor]): Future[ExecutionPlan] = Future.successful(new ExecutionPlan {
            override val tests: Vector[TestDescriptor] = selected
            override def execute(context: RunExecutionContext): Future[ProviderOutcome] = Future.successful(ProviderOutcome(Vector.empty, Vector.empty, cancelled = false))
          })
        }
        session(Vector(() => contributed(incompleteProvider, "IncompleteSuite", Vector("one"))), new RecordingSink).execute(RunId("incomplete"), RunRequest(identity, Selection.All, inherited))
      }.flatMap { incomplete =>
        verify(!incomplete.successful && incomplete.failures.exists(_.phase == FailurePhase.Transport), "A provider's incomplete terminal set must never report success")
        val reintroducingProvider = new ExecutionProvider {
          override def resolve(tests: Vector[TestDescriptor], overrides: RunOverrides): Either[Failure, Vector[TestDescriptor]] = Right(tests :+ tests.head.copy(id = tests.head.id.copy(path = Vector("unselected"))))
          override def plan(tests: Vector[TestDescriptor]): Future[ExecutionPlan] = throw new IllegalStateException("Invalid resolution must not reach planning")
        }
        val invalid = session(Vector(() => contributed(reintroducingProvider, "InvalidSuite", Vector("selected", "unselected"))), new RecordingSink)
        val selectedId = catalogue(invalid).tests.head.id
        verify(invalid.resolve(RunRequest(identity, Selection.Only(Vector.empty, Vector(selectedId)), inherited)).isLeft, "Provider resolution must not reintroduce an unselected registered test")
        ProviderBoundaryFixtures.run(identity, verify)
      }.map { _ =>
        println(s"BASE_RUNNER_FIXTURES_OK checks=${checks.get()} sessions=isolated finalization=awaited")
      }
    }
  }
}
