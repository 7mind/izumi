package izumi.distage.testkit.runner.di

import cats.effect.IO
import distage.{Activation, BootstrapModuleDef, DIKey, ModuleDef, TagK}
import izumi.distage.config.model.AppConfig
import izumi.distage.framework.config.PlanningOptions
import izumi.distage.framework.model.ActivationInfo
import izumi.distage.framework.services.{ConfigLoader, ModuleProvider}
import izumi.distage.plugins.PluginConfig
import izumi.distage.roles.model.meta.RolesInfo
import izumi.distage.testkit.model.{TestActivationStrategy, TestConfig}
import izumi.distage.testkit.protocol.*
import izumi.distage.testkit.runner.*
import izumi.distage.testkit.runner.impl.services.BootstrapFactory
import izumi.distage.testkit.runner.spec.Spec1
import izumi.functional.lifecycle.Lifecycle
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.language.Quirks.Discarder
import izumi.logstage.api.IzLogger
import izumi.logstage.api.logger.LogRouter
import izumi.logstage.api.routing.StaticLogRouter

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.control.NonFatal

private[di] object SpecBootstrapFixtures {
  private final case class Marker(owner: String)
  private final class Resource(val marker: Marker)

  private final class Statistics(owner: String) extends ResourceStatistics {
    val marker: Marker = Marker(owner)
    val configurations = new AtomicInteger(0)
    val loaders = new AtomicInteger(0)
    val loads = new AtomicInteger(0)
    val providers = new AtomicInteger(0)
    val bootstrapModules = new AtomicInteger(0)
    val appModules = new AtomicInteger(0)
    val matches = new AtomicInteger(0)
    var observed = Vector.empty[Resource]
    def body(resource: Resource, configured: Marker): Unit = synchronized {
      bodies.incrementAndGet().discard()
      if ((resource.marker eq marker) && (configured eq marker)) matches.incrementAndGet().discard()
      observed :+= resource
    }
  }

  private final class OwnerGate(expected: Int, label: String, verify: (String, Boolean) => Unit) {
    private val open = Promise[Unit]()
    private var entered = Vector.empty[Statistics]
    def enter(stats: Statistics): Future[Unit] = synchronized {
      try {
        require(!entered.contains(stats), "Custom bootstrap owner entered its resource gate twice")
        entered :+= stats
        require(entered.size <= expected, "Custom bootstrap resource gate admitted too many owners")
        if (entered.size == expected) {
          verify(label + " custom bootstrap keeps every owner resource acquired before opening the gate", entered.forall(owner => owner.acquired.get() == 1 && owner.released.get() == 0 && owner.bodies.get() == 2))
          open.success(()).discard()
        }
        open.future
      } catch {
        case NonFatal(cause) => open.tryFailure(cause).discard(); throw cause
      }
    }
  }

  private final class DelegatingBootstrap(stats: Statistics) extends ProviderFixturePlatform.BootstrapFactoryBase {
    override def makeConfigLoader(name: String, logger: IzLogger): ConfigLoader = {
      stats.loaders.incrementAndGet().discard()
      BootstrapFactory.Impl.makeConfigLoader(name, logger).map { loaded => stats.loads.incrementAndGet().discard(); loaded }
    }

    override def makeModuleProvider[F[_]: TagK](
      options: PlanningOptions,
      config: AppConfig,
      router: LogRouter,
      roles: RolesInfo,
      info: ActivationInfo,
      activation: Activation,
    ): ModuleProvider = {
      stats.providers.incrementAndGet().discard()
      BootstrapFactory.Impl.makeModuleProvider[F](options, config, router, roles, info, activation)
        .mapBootstrap { modules =>
          stats.bootstrapModules.incrementAndGet().discard()
          modules :+ new BootstrapModuleDef { make[Marker].fromValue(stats.marker).exposed }
        }
        .mapApp { modules =>
          stats.appModules.incrementAndGet().discard()
          modules :+ new ModuleDef {
            make[Resource].fromResource { (marker: Marker) =>
              Lifecycle.make[Identity, Resource] { stats.acquired.incrementAndGet().discard(); new Resource(marker) } { _ => stats.released.incrementAndGet().discard() }
            }
          }
        }
    }
  }

  private final class Suite(stats: Statistics, gate: OwnerGate) extends Spec1[IO] {
    override protected def config: TestConfig = {
      stats.configurations.incrementAndGet().discard()
      TestConfig.empty.copy(
        pluginConfig = PluginConfig.empty,
        bootstrapFactory = new DelegatingBootstrap(stats),
        activationStrategy = TestActivationStrategy.IgnoreConfig,
        parallelTests = TestConfig.Parallelism.Sequential,
        memoizationRoots = TestConfig.PriorityAxisDIKeys.fromSet(Set(DIKey[Resource])),
      )
    }
    "bootstrap" should {
      "first" in { (resource: Resource, marker: Marker) => IO(stats.body(resource, marker)) }
      "second" in { (resource: Resource, marker: Marker) => IO(stats.body(resource, marker)).flatMap(_ => IO.fromFuture(IO(gate.enter(stats)))) }
    }
  }

  def run(context: ExecutionContext, verify: (String, Boolean) => Unit): Future[Unit] = {
    implicit val ec: ExecutionContext = context
    val original = StaticLogRouter.instance.get()
    val sentinel = new StaticLogRouter
    StaticLogRouter.instance.setup(sentinel)
    exercise(Vector("first"), sentinel, context, verify)
      .flatMap(_ => exercise(Vector("repeated"), sentinel, context, verify))
      .flatMap(_ => exercise(Vector("concurrent-first", "concurrent-second"), sentinel, context, verify))
      .andThen { case _ => StaticLogRouter.instance.setup(original) }
  }

  private def exercise(owners: Vector[String], sentinel: LogRouter, context: ExecutionContext, verify: (String, Boolean) => Unit): Future[Unit] = {
    implicit val ec: ExecutionContext = context
    val gate = new OwnerGate(owners.size, owners.mkString(","), verify)
    val runs = owners.map { owner =>
      val stats = new Statistics(owner)
      val identity = CatalogueIdentity(BuildId("custom-bootstrap"), BuildTargetId("bootstrap-target"), CatalogueId(owner))
      var events = Vector.empty[ProtocolMessage.Event]
      var releasesAtFinish = Vector.empty[Int]
      val sink = new EventSink { override def accept(event: ProtocolMessage.Event): Unit = synchronized {
        events :+= event
        event.event match {
          case _: RunEvent.Finished => releasesAtFinish :+= stats.released.get()
          case _ => ()
        }
      } }
      val session = new RunSession(identity, Vector(() => new Suite(stats, gate)), context, sink)
      val catalogue = session.discover().fold(failure => throw new IllegalStateException(failure.message), value => value)
      verify(owner + " custom bootstrap discovery leaves hooks and resources suspended", catalogue.tests.size == 2 && stats.configurations.get() == 0 && stats.loaders.get() == 0 && stats.providers.get() == 0 && stats.acquired.get() == 0 && stats.bodies.get() == 0 && events.isEmpty && (StaticLogRouter.instance.get() eq sentinel))
      val request = RunRequest(identity, Selection.All, RunOverrides(Vector.empty, Vector.empty, MemoizationOverride.Inherit))
      val resolved = session.resolve(request).fold(failure => throw new IllegalStateException(failure.message), value => value)
      verify(owner + " custom bootstrap retains its configuration dispatch and snapshot", stats.configurations.get() == 1 && stats.loaders.get() == 1 && stats.loads.get() == 1 && stats.providers.get() == 0 && stats.acquired.get() == 0 && stats.bodies.get() == 0)
      session.plan(resolved).flatMap { result =>
        val planned = result.fold(failure => throw new IllegalStateException(failure.message), value => value)
        verify(owner + " custom bootstrap planning preserves global router and deferred resources", (StaticLogRouter.instance.get() eq sentinel) && stats.providers.get() == 1 && stats.bootstrapModules.get() == 1 && stats.appModules.get() == 1 && stats.acquired.get() == 0 && stats.bodies.get() == 0)
        session.execute(RunId(owner), planned).map { outcome =>
          verify(owner + " custom bootstrap and app definitions reach the selected bodies", outcome.successful && outcome.results.map(_.id).toSet == catalogue.tests.map(_.id).toSet && stats.bodies.get() == 2 && stats.matches.get() == 2)
          verify(owner + " custom bootstrap memoizes and releases its own resource before completion", stats.observed.size == 2 && (stats.observed.head eq stats.observed(1)) && stats.acquired.get() == 1 && stats.released.get() == 1 && releasesAtFinish == Vector(1) && events.last.event == RunEvent.Finished(outcome.run, outcome))
          verify(owner + " custom bootstrap execution leaves global router and hooks unchanged", (StaticLogRouter.instance.get() eq sentinel) && stats.loaders.get() == 1 && stats.loads.get() == 1 && stats.providers.get() == 1 && stats.bootstrapModules.get() == 1 && stats.appModules.get() == 1)
          println("DISTAGE_SPEC_CUSTOM_BOOTSTRAP owner=" + owner + " results=" + outcome.results.size + " acquired=" + stats.acquired.get() + " released=" + stats.released.get() + " matches=" + stats.matches.get())
          (catalogue.tests.map(_.id), stats.observed.head)
        }
      }
    }
    Future.sequence(runs).map { completed =>
      verify(owners.mkString(",") + " custom bootstrap owners keep stable IDs and distinct resources", completed.map(_._1).distinct.size == 1 && completed.map(_._2).distinct.size == owners.size)
    }
  }
}
