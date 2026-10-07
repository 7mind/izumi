package izumi.distage.testkit.runner.di

import izumi.distage.plugins.PluginConfig
import izumi.distage.plugins.load.{LoadedPlugins, PluginLoader, PluginLoaderFactory, PluginPackageCache}
import izumi.distage.testkit.model.SuiteId as EngineSuiteId
import izumi.distage.testkit.protocol.*
import izumi.distage.testkit.runner.*
import izumi.distage.testkit.runner.spec.SpecIdentity
import izumi.fundamentals.platform.language.Quirks.Discarder

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

private[di] object PluginLoaderFactoryFixtures {
  private final val ConcurrentLookups = 32

  private final class Statistics {
    val created = new AtomicInteger(0)
    val bodies = new AtomicInteger(0)
    private var caches = Vector.empty[PluginPackageCache]
    def record(cache: PluginPackageCache): Unit = synchronized { caches :+= cache; created.incrementAndGet().discard() }
    def owners: Vector[PluginPackageCache] = synchronized { caches }
  }

  private final case class Factory(stats: Statistics) extends PluginLoaderFactory {
    override def create(packageCache: PluginPackageCache): PluginLoader = {
      stats.record(packageCache)
      new PluginLoader { override def load(config: PluginConfig): LoadedPlugins = { val _ = config; LoadedPlugins.empty } }
    }
  }

  def run(context: ExecutionContext, verify: (String, Boolean) => Unit): Future[Unit] = {
    implicit val ec: ExecutionContext = context
    val registry = new SessionPluginLoaders
    val stats = new Statistics
    val first = Factory(stats)
    val equal = Factory(stats)
    val loader = registry.load(first)
    verify("one factory reference materializes one stable loader", (registry.load(first) eq loader) && stats.created.get() == 1)
    verify("structurally equal factories retain distinct loader identities", first == equal && (registry.load(equal) ne loader) && stats.created.get() == 2)
    verify("compatible factories share one explicit owner package cache", stats.owners.size == 2 && (stats.owners.head eq stats.owners(1)))
    val other = new SessionPluginLoaders
    verify("the same factory receives distinct caches and loaders in another owner", (other.load(first) ne loader) && stats.created.get() == 3 && (stats.owners.head ne stats.owners(2)))
    verify("default factory and loader references are stable inside one owner", (registry.defaultFactory eq registry.defaultFactory) && (registry.load(registry.defaultFactory) eq registry.load(registry.defaultFactory)))
    verify("default loaders are distinct across owners", registry.load(registry.defaultFactory) ne other.load(other.defaultFactory))

    val attempts = new AtomicInteger(0)
    val original = new IllegalStateException("controlled factory creation failure")
    val failing = new PluginLoaderFactory {
      override def create(packageCache: PluginPackageCache): PluginLoader = { val _ = packageCache; attempts.incrementAndGet().discard(); throw original }
    }
    verify("factory creation failure is attempted once and retains its original exception", (Try(registry.load(failing)).failed.get eq original) && (Try(registry.load(failing)).failed.get eq original) && attempts.get() == 1)
    verify("another owner performs its own failed creation attempt", (Try(other.load(failing)).failed.get eq original) && attempts.get() == 2)
    val recursive = new PluginLoaderFactory {
      override def create(packageCache: PluginPackageCache): PluginLoader = { val _ = packageCache; registry.load(this) }
    }
    val recursion = Try(registry.load(recursive)).failed.get
    verify("recursive factory lookup fails explicitly and retains its failed snapshot", recursion.getMessage == "requirement failed: Recursive plugin loader factory materialization" && (Try(registry.load(recursive)).failed.get eq recursion))

    val concurrentStats = new Statistics
    val concurrentFactory = Factory(concurrentStats)
    Future.sequence(Vector.fill(ConcurrentLookups)(Future(registry.load(concurrentFactory)))).flatMap { loaders =>
      verify("concurrent lookups materialize one factory reference once", concurrentStats.created.get() == 1 && loaders.size == ConcurrentLookups && loaders.forall(_ eq loaders.head))
      sessions(context, verify)
    }.map { _ => println("DISTAGE_PLUGIN_LOADER_FACTORY_OK identity=reference attempts=once failures=retained discovery=suspended") }
  }

  private def sessions(context: ExecutionContext, verify: (String, Boolean) => Unit): Future[Unit] = {
    implicit val ec: ExecutionContext = context
    val stats = new Statistics
    val factory = Factory(stats)
    def run(owner: String): Future[Unit] = {
      val identity = CatalogueIdentity(BuildId("factory"), BuildTargetId("factory"), CatalogueId(owner))
      val session = new RunSession(identity, Vector("first", "second").map(name => () => new SpecIdentity {
        override protected def distageSuiteId: EngineSuiteId = EngineSuiteId(name)
        override protected def makePluginLoaderFactory(): PluginLoaderFactory = factory
        "body" in { stats.bodies.incrementAndGet().discard() }
      }), context, FixtureSupport.silentSink())
      val before = stats.created.get()
      verify(owner + " discovery suspends factory materialization", session.discover().map(_.tests.size) == Right(2) && stats.created.get() == before)
      val request = RunRequest(identity, Selection.All, RunOverrides(Vector.empty, Vector.empty, MemoizationOverride.Inherit))
      session.execute(RunId(owner), request).map { outcome =>
        verify(owner + " selected suites materialize their shared factory once", outcome.successful && outcome.results.size == 2 && stats.created.get() == before + 1)
      }
    }
    run("first-owner").flatMap(_ => run("second-owner")).map { _ =>
      verify("repeated sessions execute every body with distinct factory owners", stats.bodies.get() == 4 && stats.owners.size == 2 && (stats.owners.head ne stats.owners(1)))
    }
  }
}
