package izumi.distage.testkit.runner

import izumi.distage.testkit.protocol.*

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{ExecutionContext, Future, Promise}

private[runner] object PlanOwnershipFixtures {
  def run(identity: CatalogueIdentity, context: ExecutionContext, verify: (Boolean, String) => Unit): Future[Unit] = {
    implicit val ec: ExecutionContext = context
    Vector("inspection", "invalid-identities", "invalid-inspection", "other-provider-failure", "close-during-planning", "execution").foldLeft(Future.unit) { (before, mode) => before.flatMap { _ =>
      val closes = new AtomicInteger(0)
      val executions = new AtomicInteger(0)
      val releasing = Promise[Unit]()
      val release = Promise[Unit]()
      val acquisition = Promise[ExecutionPlan]()
      var events = Vector.empty[ProtocolMessage.Event]
      val sink = new EventSink { override def accept(event: ProtocolMessage.Event): Unit = synchronized { events :+= event } }
      def suite(name: String, fails: Boolean): TestSuite = new TestSuite {
        override def register(registration: RegistrationContext): RegisteredSuite = {
          val descriptor = SuiteDescriptor(SuiteId(name), name)
          val test = TestDescriptor(TestId(registration.target, descriptor.id, Vector("body"), None), "body", SourceLocation.Unavailable, EffectiveSettings(Vector.empty, memoization = true))
          val provider = new ExecutionProvider {
            override def resolve(selected: Vector[TestDescriptor], overrides: RunOverrides): Either[Failure, Vector[TestDescriptor]] = Right(selected)
            override def plan(selected: Vector[TestDescriptor]): Future[ExecutionPlan] = {
              if (fails) Future.failed(new IllegalStateException("Controlled other-provider planning failure"))
              else {
                val plan = new ExecutionPlan {
                  override val tests: Vector[TestDescriptor] = if (mode == "invalid-identities") Vector.empty else selected
                  override val inspection: PlanInspection = if (mode == "invalid-inspection") PlanInspection.individualTests(Vector.empty) else PlanInspection.individualTests(selected.map(_.id))
                  override def execute(execution: RunExecutionContext): Future[ProviderOutcome] = {
                    val _ = executions.incrementAndGet()
                    val result = TestResult(test.id, TestStatus.Succeeded, None, 0L)
                    execution.emit(ProviderEvent.TestStarted(test.id))
                    execution.emit(ProviderEvent.TestCompleted(result))
                    Future.successful(ProviderOutcome(Vector(result), Vector.empty, cancelled = false))
                  }
                  override def close(): Future[Unit] = {
                    val _ = closes.incrementAndGet()
                    val _ = releasing.trySuccess(())
                    release.future
                  }
                }
                if (mode == "close-during-planning") { val _ = acquisition.success(plan); release.future.map(_ => plan) }
                else Future.successful(plan)
              }
            }
          }
          RegisteredSuite(descriptor, Vector(test), provider)
        }
      }
      val factories = Vector(() => suite("OwnedPlan", false)) ++ (if (mode == "other-provider-failure") Vector(() => suite("FailedPlan", true)) else Vector.empty)
      val session = new RunSession(identity, factories, context, sink)
      val request = RunRequest(identity, Selection.All, RunOverrides(Vector.empty, Vector.empty, MemoizationOverride.Inherit))
      val selected = session.resolve(request).fold(failure => throw new IllegalStateException(failure.message), value => value)
      val planning = session.plan(selected)
      if (mode == "close-during-planning") {
        acquisition.future.flatMap { _ =>
          val first = session.close()
          val second = session.close()
          val held = (first eq second) && !first.isCompleted
          val _ = release.success(())
          first.flatMap(_ => planning).map { result =>
            verify(held, "Close during acquisition joins the same in-flight owner")
            verify(result.isLeft && closes.get() == 1 && executions.get() == 0, "A session closed during planning releases its returned owner and rejects execution")
          }
        }
      } else if (Set("invalid-identities", "invalid-inspection", "other-provider-failure").contains(mode)) {
        releasing.future.flatMap { _ =>
          val held = !planning.isCompleted && closes.get() == 1 && executions.get() == 0
          val _ = release.success(())
          planning.flatMap { result =>
            verify(held, mode + " rejection waits for the invalid or partial plan's finalizer")
            verify(result.left.exists(_.phase == FailurePhase.Planning), mode + " preserves a planning rejection")
            session.close().map(_ => verify(closes.get() == 1, mode + " repeated close releases exactly once"))
          }
        }
      } else planning.flatMap { result =>
        val planned = result.fold(failure => throw new IllegalStateException(failure.message), value => value)
        if (mode == "inspection") {
          val first = session.close()
          val second = session.close()
          releasing.future.flatMap { _ =>
            val held = (first eq second) && !first.isCompleted && executions.get() == 0
            val _ = release.success(())
            first.map { _ =>
              verify(held, "Inspection-only close joins held finalization without executing tests")
              verify(closes.get() == 1 && events.isEmpty, "Inspection-only ownership releases once without execution events")
            }
          }
        } else {
          val executed = session.execute(RunId("owned-execution"), planned)
          releasing.future.flatMap { _ =>
            val held = !executed.isCompleted && !sink.synchronized(events).exists(_.event.isInstanceOf[RunEvent.Finished])
            val _ = release.success(())
            executed.flatMap { outcome =>
              verify(held, "Execution completion and Finished wait for owned-plan release")
              verify(outcome.successful && executions.get() == 1 && closes.get() == 1 && sink.synchronized(events).last.event == RunEvent.Finished(outcome.run, outcome), "Owned execution releases once before its terminal event")
              session.close().map(_ => verify(closes.get() == 1, "Closing a completed execution preserves single release"))
            }
          }
        }
      }
    } }.map { _ => println("PLAN_OWNERSHIP_CONTRACTS_OK cases=6") }
  }
}
