package izumi.distage.testkit.runner

import izumi.distage.testkit.protocol.*

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{ExecutionContext, Future}

private[runner] object PlanAggregationFixtures {
  def run(identity: CatalogueIdentity, context: ExecutionContext, verify: (Boolean, String) => Unit): Future[Unit] = {
    implicit val ec: ExecutionContext = context
    val executions = new AtomicInteger(0)
    val sink = new FixtureSupport.RecordingSink
    def events: Vector[ProtocolMessage.Event] = sink.events
    def suite(name: String): TestSuite = new TestSuite {
      override def register(registration: RegistrationContext): RegisteredSuite = {
        val descriptor = TestDescriptor(TestId(identity.target, SuiteId(name), Vector("test"), None), "test", SourceLocation.Unavailable, EffectiveSettings(Vector.empty, memoization = true))
        val provider = new FixtureSupport.Provider {
          override def plan(selected: Vector[TestDescriptor]): Future[ExecutionPlan] = Future.successful(new FixtureSupport.Plan(selected) {
            override val inspection: PlanInspection = PlanInspection(
              Vector(DependencyKey(DependencyKeyId(7), "same label"), DependencyKey(DependencyKeyId(13), "same label")),
              Vector(
                PlanScope(PlanScopeId(Vector(9)), PlanScopeKind.Runtime, Vector(descriptor.id), Vector(PlanStep(DependencyKeyId(7), PlanOperation.UseInstance, Vector.empty))),
                PlanScope(PlanScopeId(Vector(9, 2)), PlanScopeKind.Test, Vector(descriptor.id), Vector(PlanStep(DependencyKeyId(13), PlanOperation.CallProvider, Vector(DependencyKeyId(7))))),
              ),
              Vector.empty,
            )
            override def execute(execution: RunExecutionContext): Future[ProviderOutcome] = {
              val _ = executions.incrementAndGet()
              val result = TestResult(descriptor.id, TestStatus.Succeeded, None, 0L)
              execution.emit(ProviderEvent.TestStarted(descriptor.id))
              execution.emit(ProviderEvent.TestCompleted(result))
              Future.successful(ProviderOutcome(Vector(result), Vector.empty, cancelled = false))
            }
          })
        }
        RegisteredSuite(SuiteDescriptor(descriptor.id.suite, name), Vector(descriptor), provider)
      }
    }
    val session = new RunSession(identity, Vector(() => suite("FirstPlanProvider"), () => suite("SecondPlanProvider")), context, sink)
    val request = RunRequest(identity, Selection.All, RunOverrides(Vector.empty, Vector.empty, MemoizationOverride.Inherit))
    val resolved = session.resolve(request).fold(failure => throw new IllegalStateException(failure.message), value => value)
    session.plan(resolved).flatMap {
      case Left(failure) => Future.failed(new IllegalStateException(failure.message))
      case Right(planned) =>
        val inspection = planned.description.inspection
        val roots = inspection.scopes.filter(_.kind == PlanScopeKind.Runtime)
        val leaves = inspection.scopes.filter(_.kind == PlanScopeKind.Test)
        verify(executions.get() == 0 && events.isEmpty && planned.tests == resolved.tests, "Provider plan aggregation must preserve the selection without execution")
        verify(inspection.keys.size == 4 && inspection.keys.map(_.id).distinct.size == 4 && inspection.keys.map(_.displayName).distinct.size == 1 && roots.size == 2 && roots.map(_.id).distinct.size == 2, "Independent nonempty provider key tables and roots must receive distinct identities")
        verify(leaves.size == 2 && leaves.forall { leaf =>
          val parent = roots.find(root => leaf.id.path.startsWith(root.id.path)).getOrElse(throw new IllegalStateException("Provider root is missing"))
          leaf.tests == parent.tests && leaf.steps.size == 1 && leaf.steps.head.dependencies == parent.steps.map(_.key) && leaf.steps.head.key != parent.steps.head.key
        }, "Each remapped dependency edge must stay within its owning provider graph")
        val message = ProtocolMessage.Planned(RunId("aggregate-plan"), planned.description)
        verify(ProtocolCodec.decode(ProtocolCodec.encode(message)) == Right(message), "Aggregated nonempty provider graphs must round-trip")
        session.execute(RunId("aggregate-plan"), planned).map { outcome =>
          verify(outcome.successful && outcome.results.map(_.id).toSet == resolved.tests.map(_.id).toSet && executions.get() == 2 && events.last.event == RunEvent.Finished(outcome.run, outcome) && events.forall(event => ProtocolCodec.decode(ProtocolCodec.encode(event)) == Right(event)), "Execution and terminal events must retain both remapped provider selections")
        }
    }
  }
}
