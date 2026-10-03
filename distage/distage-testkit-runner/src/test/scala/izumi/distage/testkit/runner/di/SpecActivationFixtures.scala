package izumi.distage.testkit.runner.di

import distage.{Activation, Axis, DIKey, ModuleDef}
import izumi.distage.plugins.PluginConfig
import izumi.distage.testkit.model.{TestActivationStrategy, TestConfig}
import izumi.distage.testkit.protocol.*
import izumi.distage.testkit.runner.*
import izumi.distage.testkit.runner.spec.SpecIdentity
import izumi.functional.lifecycle.Lifecycle
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.language.Quirks.Discarder

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{ExecutionContext, Future}

private[di] object SpecActivationFixtures {
  private object Mode extends Axis {
    case object Suite extends AxisChoiceDef
    case object Config extends AxisChoiceDef
    case object Explicit extends AxisChoiceDef
  }
  private final class Resource
  private final case class Pair(first: Resource, second: Resource)
  private final case class Chosen(value: String)
  private final case class Request(name: String, suiteChoice: Boolean, overrides: RunOverrides, unknownId: Boolean, expected: Option[String], resources: Int)

  def run(context: ExecutionContext, verify: (String, Boolean) => Unit): Future[Unit] = {
    implicit val ec: ExecutionContext = context
    def choice(value: Axis.AxisChoice): AxisChoice = AxisChoice(AxisId(Mode.name), AxisValue(value.value))
    val requests = Vector(
      Request("loaded-config", false, RunOverrides(Vector.empty, Vector.empty, MemoizationOverride.Inherit), false, Some(Mode.Config.value), 1),
      Request("suite-precedence", true, RunOverrides(Vector.empty, Vector.empty, MemoizationOverride.Inherit), false, Some(Mode.Suite.value), 1),
      Request("explicit-precedence", true, RunOverrides(Vector(choice(Mode.Explicit)), Vector.empty, MemoizationOverride.Inherit), false, Some(Mode.Explicit.value), 1),
      Request("effective-filter", true, RunOverrides(Vector(choice(Mode.Explicit)), Vector(choice(Mode.Explicit)), MemoizationOverride.Inherit), false, Some(Mode.Explicit.value), 1),
      Request("memoization-disabled", true, RunOverrides(Vector.empty, Vector.empty, MemoizationOverride.Disabled), false, Some(Mode.Suite.value), 2),
      Request("unknown-value", true, RunOverrides(Vector(AxisChoice(AxisId(Mode.name), AxisValue("unknown"))), Vector.empty, MemoizationOverride.Inherit), false, None, 0),
      Request("unknown-axis", true, RunOverrides(Vector(AxisChoice(AxisId("unknown"), AxisValue("unknown"))), Vector.empty, MemoizationOverride.Inherit), false, None, 0),
      Request("unknown-filter", true, RunOverrides(Vector.empty, Vector(AxisChoice(AxisId(Mode.name), AxisValue("unknown"))), MemoizationOverride.Inherit), false, None, 0),
      Request("empty-filter", true, RunOverrides(Vector.empty, Vector(choice(Mode.Config)), MemoizationOverride.Inherit), false, None, 0),
      Request("unknown-id", true, RunOverrides(Vector.empty, Vector.empty, MemoizationOverride.Inherit), true, None, 0),
    )
    requests.foldLeft(Future.successful(())) { (before, request) => before.flatMap { _ =>
      val configurations = new AtomicInteger(0)
      val acquired = new AtomicInteger(0)
      val released = new AtomicInteger(0)
      val bodies = new AtomicInteger(0)
      val shared = new AtomicInteger(0)
      val wrongChoice = new AtomicInteger(0)
      val definitions = new ModuleDef {
        make[Chosen].tagged(Mode.Suite).fromValue(Chosen(Mode.Suite.value))
        make[Chosen].tagged(Mode.Config).fromValue(Chosen(Mode.Config.value))
        make[Chosen].tagged(Mode.Explicit).fromValue(Chosen(Mode.Explicit.value))
        make[Resource].fromResource(() => Lifecycle.make[Identity, Resource] { acquired.incrementAndGet().discard(); new Resource } { _ => released.incrementAndGet().discard() })
        make[Pair].from((first: Resource, second: Resource) => Pair(first, second))
      }
      val suite = new SpecIdentity {
        override protected def config: TestConfig = {
          configurations.incrementAndGet().discard()
          TestConfig.empty.copy(
            pluginConfig = PluginConfig.constUnchecked(definitions),
            activation = if (request.suiteChoice) Activation(Mode -> Mode.Suite) else Activation.empty,
            activationStrategy = TestActivationStrategy.LoadConfig(ignoreUnknown = false, warnUnset = false),
            configOverrides = Some(ProviderFixturePlatform.activationConfig(choice(Mode.Config))),
            memoizationRoots = TestConfig.PriorityAxisDIKeys.fromSet(Set(DIKey[Resource])),
          )
        }
        private def body(chosen: Chosen, resource: Resource, pair: Pair): Unit = {
          bodies.incrementAndGet().discard()
          if (!request.expected.contains(chosen.value)) wrongChoice.incrementAndGet().discard()
          if ((resource eq pair.first) && (resource eq pair.second)) shared.incrementAndGet().discard()
        }
        "activation" should {
          "first" in { (chosen: Chosen, resource: Resource, pair: Pair) => body(chosen, resource, pair) }
          "second" in { (chosen: Chosen, resource: Resource, pair: Pair) => body(chosen, resource, pair) }
        }
      }
      val identity = CatalogueIdentity(BuildId("spec-activation"), BuildTargetId("spec-target"), CatalogueId(request.name))
      val sink = new EventSink { override def accept(event: ProtocolMessage.Event): Unit = () }
      val session = new RunSession(identity, Vector(() => suite), context, sink)
      val catalogue = session.discover().fold(failure => throw new IllegalStateException(failure.message), value => value)
      verify(request.name + " discovery suspends activation inputs and resources", configurations.get() == 0 && acquired.get() == 0 && bodies.get() == 0)
      val selection = if (request.unknownId) Selection.Only(Vector.empty, Vector(catalogue.tests.head.id.copy(path = Vector("missing")))) else Selection.All
      val runRequest = RunRequest(identity, selection, request.overrides)
      val resolution = session.resolve(runRequest)
      request.expected match {
        case Some(expected) =>
          verify(request.name + " resolves effective activation before filtering", resolution.exists(value => value.tests.size == 2 && value.tests.forall(test => test.settings.axes.contains(AxisChoice(AxisId(Mode.name), AxisValue(expected))) && test.settings.memoization == (request.overrides.memoization != MemoizationOverride.Disabled))))
        case None => verify(request.name + " rejects its explicit selection before provisioning", resolution.left.toOption.exists(failure => failure.phase == FailurePhase.Selection && failure.message.nonEmpty))
      }
      val execution = resolution match {
        case Left(_) => session.execute(RunId(request.name), runRequest)
        case Right(resolved) => session.plan(resolved).flatMap {
          case Left(failure) => Future.failed(new IllegalStateException(failure.message))
          case Right(planned) =>
            val description = planned.description
            verify(request.name + " plan output retains resolved activation and logical identities", description.selection == resolved.description && description.selection.tests.map(_.id) == catalogue.tests.map(_.id))
            val allocations = description.inspection.scopes.filter(_.steps.exists(step => description.inspection.keys.exists(key => key.id == step.key && key.displayName == DIKey[Resource].toString) && step.operation == PlanOperation.AllocateResource))
            val sharedScope = if (request.overrides.memoization == MemoizationOverride.Disabled) {
              allocations.size == 2 && allocations.forall(scope => scope.kind == PlanScopeKind.Test && scope.tests.size == 1)
            } else allocations.size == 1 && allocations.head.kind == PlanScopeKind.Memoization && allocations.head.tests.toSet == catalogue.tests.map(_.id).toSet
            verify(request.name + " plan output describes actual resource sharing boundaries", sharedScope)
            verify(request.name + " plan inspection provisions no application resources or bodies", acquired.get() == 0 && released.get() == 0 && bodies.get() == 0 && shared.get() == 0)
            val frame = ProtocolMessage.Planned(RunId(request.name), description)
            verify(request.name + " plan output round-trips through the protocol", ProtocolCodec.decode(ProtocolCodec.encode(frame)) == Right(frame))
            session.execute(RunId(request.name), planned)
        }
      }
      execution.map { outcome =>
        val expectedBodies = if (request.expected.isDefined) 2 else 0
        verify(request.name + " executes its resolved choice with its intended lifetime", acquired.get() == request.resources && released.get() == request.resources && bodies.get() == expectedBodies && shared.get() == expectedBodies && wrongChoice.get() == 0)
        verify(request.name + " execution agrees with resolution or explains rejection", if (request.expected.isDefined) outcome.successful && outcome.results.map(_.id) == catalogue.tests.map(_.id) else !outcome.successful && outcome.results.isEmpty && outcome.failures.size == 1 && outcome.failures.head.phase == FailurePhase.Selection)
        verify(request.name + " retains one suite configuration snapshot", configurations.get() == (if (request.unknownId) 0 else 1))
        println("DISTAGE_SPEC_ACTIVATION name=" + request.name + " resources=" + acquired.get() + " bodies=" + bodies.get() + " successful=" + outcome.successful)
      }
    } }
  }
}
