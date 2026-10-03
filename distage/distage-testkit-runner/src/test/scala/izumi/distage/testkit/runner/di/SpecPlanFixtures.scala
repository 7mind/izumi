package izumi.distage.testkit.runner.di

import distage.{BootstrapModuleDef, DIKey, ModuleDef}
import izumi.distage.plugins.PluginConfig
import izumi.distage.roles.launcher.AppShutdownInitiator
import izumi.distage.testkit.model.{SuiteId as EngineSuiteId, TestConfig}
import izumi.distage.testkit.protocol.*
import izumi.distage.testkit.runner.*
import izumi.distage.testkit.runner.spec.{AnyWordSpec, SpecIdentity}
import izumi.functional.lifecycle.Lifecycle
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.language.Quirks.Discarder

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{ExecutionContext, Future}

private[di] object SpecPlanFixtures {
  private final class FirstResource
  private final class SecondResource(val first: FirstResource)
  private final case class Pair(first: FirstResource, second: FirstResource)
  private final class Conflicting
  private final val CollisionHash = 17
  private final class CollidingService {
    override def hashCode(): Int = CollisionHash
  }

  def run(context: ExecutionContext, verify: (String, Boolean) => Unit): Future[Unit] = {
    implicit val ec: ExecutionContext = context
    Vector(MemoizationOverride.Inherit, MemoizationOverride.Disabled).foldLeft(Future.successful(())) { (before, memoization) =>
      before.flatMap(_ => nested(memoization, context, verify))
    }.flatMap(_ => planningFailure(context, verify)).flatMap(_ => collidingKeys(context, verify))
  }

  private def nested(memoization: MemoizationOverride, context: ExecutionContext, verify: (String, Boolean) => Unit): Future[Unit] = {
    implicit val ec: ExecutionContext = context
    val acquired = new AtomicInteger(0)
    val released = new AtomicInteger(0)
    val secondAcquired = new AtomicInteger(0)
    val secondReleased = new AtomicInteger(0)
    val bodies = new AtomicInteger(0)
    val plainBodies = new AtomicInteger(0)
    val definitions = new ModuleDef {
      make[FirstResource].fromResource(() => Lifecycle.make[Identity, FirstResource] { acquired.incrementAndGet().discard(); new FirstResource } { _ => released.incrementAndGet().discard() })
      make[SecondResource].fromResource((first: FirstResource) => Lifecycle.make[Identity, SecondResource] { secondAcquired.incrementAndGet().discard(); new SecondResource(first) } { _ => secondReleased.incrementAndGet().discard() })
      make[Pair].from((first: FirstResource, second: FirstResource) => Pair(first, second))
    }
    val bootstrap = new BootstrapModuleDef {
      make[AppShutdownInitiator].fromValue(AppShutdownInitiator.empty).exposed
    }
    def configuration(second: Boolean): TestConfig = TestConfig.empty.copy(
      pluginConfig = PluginConfig.constUnchecked(definitions),
      bootstrapOverrides = bootstrap,
      memoizationRoots = TestConfig.PriorityAxisDIKeys.fromPrioritySet(
        if (second) Map(0 -> Set(DIKey[FirstResource]), 1 -> Set(DIKey[SecondResource])) else Map(0 -> Set(DIKey[FirstResource]))
      ),
    )
    val firstSuite = new SpecIdentity {
      override protected def distageSuiteId: EngineSuiteId = EngineSuiteId("nested-first")
      override protected def config: TestConfig = configuration(true)
      "first" in { (first: FirstResource, second: SecondResource, pair: Pair) =>
        require((first eq second.first) && (first eq pair.first) && (first eq pair.second))
        bodies.incrementAndGet().discard()
      }
    }
    val secondSuite = new SpecIdentity {
      override protected def distageSuiteId: EngineSuiteId = EngineSuiteId("nested-second")
      override protected def config: TestConfig = configuration(false)
      "second" in { (first: FirstResource, pair: Pair) =>
        require((first eq pair.first) && (first eq pair.second))
        bodies.incrementAndGet().discard()
      }
    }
    val plainSuite = new AnyWordSpec { "plain" in { plainBodies.incrementAndGet().discard() } }
    val identity = CatalogueIdentity(BuildId("plan-fixture"), BuildTargetId("plan-target"), CatalogueId("nested-" + memoization))
    var events = Vector.empty[ProtocolMessage.Event]
    var finalized = false
    val sink = new EventSink { override def accept(event: ProtocolMessage.Event): Unit = synchronized {
      events :+= event
      event.event match {
        case _: RunEvent.Finished => finalized = acquired.get() == released.get() && secondAcquired.get() == secondReleased.get()
        case _ => ()
      }
    } }
    val session = new RunSession(identity, Vector(() => plainSuite, () => firstSuite, () => secondSuite), context, sink)
    val catalogue = session.discover().fold(failure => throw new IllegalStateException(failure.message), value => value)
    verify("mixed plan discovery leaves all providers suspended", catalogue.tests.size == 3 && acquired.get() == 0 && secondAcquired.get() == 0 && bodies.get() == 0 && plainBodies.get() == 0)
    val request = RunRequest(identity, Selection.All, RunOverrides(Vector.empty, Vector.empty, memoization))
    val resolved = session.resolve(request).fold(failure => throw new IllegalStateException(failure.message), value => value)
    verify("mixed plan resolution preserves all logical IDs", resolved.tests.map(_.id) == catalogue.tests.map(_.id) && acquired.get() == 0 && secondAcquired.get() == 0)
    session.plan(resolved).flatMap {
      case Left(failure) => Future.failed(new IllegalStateException(failure.message))
      case Right(planned) =>
        val inspection = planned.description.inspection
        val leaves = inspection.scopes.filter(_.kind == PlanScopeKind.Test)
        verify("mixed plan leaves cover all providers with distinct scope IDs", leaves.flatMap(_.tests).toSet == catalogue.tests.map(_.id).toSet && leaves.size == 3 && inspection.scopes.map(_.id).distinct.size == inspection.scopes.size)
        def allocations(key: DIKey): Vector[PlanScope] = inspection.scopes.filter(_.steps.exists(step => inspection.keys.exists(description => description.id == step.key && description.displayName == key.toString) && step.operation == PlanOperation.AllocateResource))
        val first = allocations(DIKey[FirstResource])
        val second = allocations(DIKey[SecondResource])
        val distageIds = catalogue.tests.filter(_.id.suite.value.startsWith("nested-")).map(_.id)
        val expected = if (memoization == MemoizationOverride.Disabled) {
          first.size == 2 && first.forall(scope => scope.kind == PlanScopeKind.Test && scope.tests.size == 1) && second.size == 1 && second.head.kind == PlanScopeKind.Test
        } else {
          first.size == 1 && second.size == 1 && first.head.kind == PlanScopeKind.Memoization && second.head.kind == PlanScopeKind.Memoization &&
          first.head.tests.toSet == distageIds.toSet && second.head.tests == distageIds.take(1) && second.head.id.path.startsWith(first.head.id.path) && second.head.id.path.size > first.head.id.path.size
        }
        verify("nested plan describes shared ancestors and narrower descendants", expected)
        val plain = leaves.find(_.tests.head.suite.value == plainSuite.getClass.getName).getOrElse(throw new IllegalStateException("Plain plan leaf is missing"))
        verify("plain and distage roots retain independent sharing boundaries", plain.id.path.size == 1 && plain.steps.isEmpty && inspection.scopes.filter(_.kind == PlanScopeKind.Runtime).forall(scope => !scope.tests.contains(plain.tests.head)))
        val message = ProtocolMessage.Planned(RunId("nested-plan"), planned.description)
        verify("mixed nested plan round-trips with selected effective settings", ProtocolCodec.decode(ProtocolCodec.encode(message)) == Right(message))
        verify("nested inspection acquires no application resources or bodies", acquired.get() == 0 && released.get() == 0 && secondAcquired.get() == 0 && secondReleased.get() == 0 && bodies.get() == 0 && plainBodies.get() == 0 && events.isEmpty)
        session.execute(RunId("nested-plan"), planned).map { outcome =>
          val firstCount = if (memoization == MemoizationOverride.Disabled) 2 else 1
          verify("nested execution agrees with plan lifetimes and within-test sharing", outcome.successful && outcome.results.map(_.id).toSet == catalogue.tests.map(_.id).toSet && acquired.get() == firstCount && released.get() == firstCount && secondAcquired.get() == 1 && secondReleased.get() == 1 && bodies.get() == 2 && plainBodies.get() == 1)
          verify("mixed nested execution finishes after both resource levels release", finalized && events.last.event == RunEvent.Finished(outcome.run, outcome) && events.map(_.sequence) == events.indices.map(_.toLong).toVector)
        }
    }
  }

  private def planningFailure(context: ExecutionContext, verify: (String, Boolean) => Unit): Future[Unit] = {
    implicit val ec: ExecutionContext = context
    val bodies = new AtomicInteger(0)
    val configurations = new AtomicInteger(0)
    val definitions = new ModuleDef {
      make[Conflicting].from(() => new Conflicting)
      make[Conflicting].from(() => new Conflicting)
    }
    val suite = new SpecIdentity {
      override protected def config: TestConfig = { configurations.incrementAndGet().discard(); TestConfig.empty.copy(pluginConfig = PluginConfig.constUnchecked(definitions)) }
      "first" in { (_: Conflicting) => bodies.incrementAndGet().discard() }
      "second" in { (_: Conflicting) => bodies.incrementAndGet().discard() }
    }
    val identity = CatalogueIdentity(BuildId("plan-fixture"), BuildTargetId("plan-target"), CatalogueId("planning-failure"))
    val session = new RunSession(identity, Vector(() => suite), context, new EventSink { override def accept(event: ProtocolMessage.Event): Unit = { val _ = event } })
    val catalogue = session.discover().fold(failure => throw new IllegalStateException(failure.message), value => value)
    verify("failed plan discovery suspends configuration and bodies", configurations.get() == 0 && bodies.get() == 0)
    val request = RunRequest(identity, Selection.All, RunOverrides(Vector.empty, Vector.empty, MemoizationOverride.Inherit))
    val resolved = session.resolve(request).fold(failure => throw new IllegalStateException(failure.message), value => value)
    verify("failed plan resolution preserves the selected identities", resolved.tests.map(_.id) == catalogue.tests.map(_.id) && configurations.get() == 1 && bodies.get() == 0)
    session.plan(resolved).flatMap {
      case Left(failure) => Future.failed(new IllegalStateException(failure.message))
      case Right(planned) =>
        val inspection = planned.description.inspection
        verify("planning conflicts appear before execution as per-test Planning failures", inspection.scopes.isEmpty && inspection.failures.flatMap(_.tests).toSet == catalogue.tests.map(_.id).toSet && inspection.failures.forall(value => value.failure.phase == FailurePhase.Planning && value.failure.exceptionClass.endsWith("InjectorFailed") && value.failure.message.nonEmpty))
        val message = ProtocolMessage.Planned(RunId("failed-plan"), planned.description)
        verify("failed plan wire output preserves failures without executing bodies", ProtocolCodec.decode(ProtocolCodec.encode(message)) == Right(message) && bodies.get() == 0)
        session.execute(RunId("failed-plan"), planned).map { outcome =>
          verify("failed plan execution retains Planning classification and runs no bodies", !outcome.successful && outcome.results.size == 2 && outcome.results.forall(_.failure.exists(_.phase == FailurePhase.Planning)) && bodies.get() == 0 && configurations.get() == 1)
        }
    }
  }

  private def collidingKeys(context: ExecutionContext, verify: (String, Boolean) => Unit): Future[Unit] = {
    implicit val ec: ExecutionContext = context
    val first = new CollidingService
    val second = new CollidingService
    val bodies = new AtomicInteger(0)
    val definitions = new ModuleDef { many[CollidingService].addValue(first).addValue(second) }
    val suite = new SpecIdentity {
      override protected def config: TestConfig = TestConfig.empty.copy(pluginConfig = PluginConfig.constUnchecked(definitions))
      "colliding values" in { (services: Set[CollidingService]) =>
        require(services.size == 2 && services.contains(first) && services.contains(second))
        bodies.incrementAndGet().discard()
      }
    }
    val identity = CatalogueIdentity(BuildId("plan-fixture"), BuildTargetId("plan-target"), CatalogueId("colliding-keys"))
    val session = new RunSession(identity, Vector(() => suite), context, new EventSink { override def accept(event: ProtocolMessage.Event): Unit = { val _ = event } })
    val catalogue = session.discover().fold(failure => throw new IllegalStateException(failure.message), value => value)
    verify("colliding set values are distinct and discovery remains suspended", first != second && first.hashCode() == second.hashCode() && bodies.get() == 0)
    val request = RunRequest(identity, Selection.All, RunOverrides(Vector.empty, Vector.empty, MemoizationOverride.Inherit))
    val resolved = session.resolve(request).fold(failure => throw new IllegalStateException(failure.message), value => value)
    session.plan(resolved).flatMap {
      case Left(failure) => Future.failed(new IllegalStateException(failure.message))
      case Right(planned) =>
        val inspection = planned.description.inspection
        val values = inspection.scopes.filter(_.kind == PlanScopeKind.Test).flatMap(_.steps).filter(_.operation == PlanOperation.UseInstance).map(_.key)
        val labels = values.map(id => inspection.keys.find(_.id == id).getOrElse(throw new IllegalStateException("Set element key is missing")).displayName)
        verify("set element key identities remain distinct when display labels coincide", values.size == 2 && values.distinct.size == 2 && labels.distinct.size == 1 && inspection.failures.isEmpty)
        val message = ProtocolMessage.Planned(RunId("colliding-keys"), planned.description)
        verify("colliding key references round-trip without executing bodies", ProtocolCodec.decode(ProtocolCodec.encode(message)) == Right(message) && bodies.get() == 0)
        session.execute(RunId("colliding-keys"), planned).map { outcome =>
          verify("both colliding set elements reach the selected test", outcome.successful && outcome.results.map(_.id) == catalogue.tests.map(_.id) && bodies.get() == 1)
          println("DISTAGE_PLAN_KEY_COLLISION_OK keys=distinct labels=shared values=2 bodies=1")
        }
    }
  }
}
