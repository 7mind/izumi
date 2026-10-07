package izumi.distage.testkit.runner.di

import distage.ModuleDef
import izumi.distage.plugins.PluginConfig
import izumi.distage.plugins.load.{LoadedPlugins, PluginLoader, PluginLoaderDefaultImpl, PluginLoaderFactory, PluginPackageCache}
import izumi.distage.testkit.model.{TestActivationStrategy, TestConfig}
import izumi.distage.testkit.protocol.*
import izumi.distage.testkit.runner.*
import izumi.distage.testkit.runner.spec.SpecIdentity

import scala.concurrent.{ExecutionContext, Future}

private[di] object SpecPluginRequestFixtures {
  private final class Marker
  private final class Statistics {
    val marker = new Marker
    var requests = Vector.empty[PluginConfig]
    var owners = Vector.empty[PluginPackageCache]
    var observed = Vector.empty[Marker]
  }
  private final class Suite(stats: Statistics) extends SpecIdentity {
    override protected def config: TestConfig = TestConfig.empty.copy(
      pluginConfig = PluginConfig.constUnchecked(new ModuleDef { make[Marker].fromValue(stats.marker) }).cachePackages(true),
      activationStrategy = TestActivationStrategy.IgnoreConfig,
    )
    override protected def makePluginLoaderFactory(): PluginLoaderFactory = new PluginLoaderFactory {
      override def create(packageCache: PluginPackageCache): PluginLoader = {
        stats.owners :+= packageCache
        val delegate = PluginLoaderDefaultImpl.withPackageCache(packageCache).map(value => value)
        new PluginLoader {
          override def load(config: PluginConfig): LoadedPlugins = {
            val request = config.snapshot().copy(debug = true)
            stats.requests :+= request
            delegate.load(request)
          }
        }
      }
    }
    "request" in { (marker: Marker) => stats.observed :+= marker }
  }

  def run(context: ExecutionContext, verify: (String, Boolean) => Unit): Future[Unit] = {
    implicit val ec: ExecutionContext = context
    exercise("first", context, verify)
      .flatMap { first => exercise("repeated", context, verify).map(second => verify("plugin requests retain distinct repeated session owners", first ne second)) }
      .flatMap { _ =>
        Future.sequence(Vector("concurrent-first", "concurrent-second").map(owner => exercise(owner, context, verify)))
          .map(owners => verify("plugin requests retain distinct concurrent session owners", owners.head ne owners(1)))
      }
  }

  private def exercise(owner: String, context: ExecutionContext, verify: (String, Boolean) => Unit): Future[PluginPackageCache] = {
    implicit val ec: ExecutionContext = context
    val stats = new Statistics
    val identity = CatalogueIdentity(BuildId("plugin-requests"), BuildTargetId("request-target"), CatalogueId(owner))
    val sink = FixtureSupport.silentSink()
    val session = new RunSession(identity, Vector(() => new Suite(stats)), context, sink)
    val catalogue = session.discover().fold(failure => throw new IllegalStateException(failure.message), value => value)
    verify(owner + " plugin request discovery leaves loading and bodies suspended", catalogue.tests.size == 1 && stats.requests.isEmpty && stats.observed.isEmpty)
    val request = RunRequest(identity, Selection.All, RunOverrides(Vector.empty, Vector.empty, MemoizationOverride.Inherit))
    val resolved = session.resolve(request).fold(failure => throw new IllegalStateException(failure.message), value => value)
    val owners = stats.owners
    verify(owner + " app and bootstrap copies use one factory owner and original cache flags", stats.requests.size == 2 && owners.size == 1 && stats.requests.forall(_.packageCacheOwner.isEmpty) && stats.requests.map(_.cachePackages) == Vector(true, false) && stats.observed.isEmpty)
    session.plan(resolved).flatMap { result =>
      val planned = result.fold(failure => throw new IllegalStateException(failure.message), value => value)
      verify(owner + " plugin request planning leaves bodies suspended", stats.observed.isEmpty)
      session.execute(RunId(owner), planned).map { outcome =>
        verify(owner + " custom static definitions reach the successful selected body", outcome.successful && outcome.results.size == 1 && stats.observed.size == 1 && (stats.observed.head eq stats.marker) && stats.requests.size == 2)
        owners.head
      }
    }
  }
}
