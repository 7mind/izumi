package izumi.distage.testkit.spec

import izumi.distage.plugins.PluginConfig
import izumi.distage.plugins.PluginBase
import izumi.distage.plugins.load.{LoadedPlugins, PluginLoaderClassgraphImpl, PluginLoaderDefaultImpl, PluginPackageCache}
import izumi.distage.testkit.spec.sessionplugins.SessionScannedPlugin

import java.util.concurrent.{Executors, TimeUnit}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.*
import java.util.concurrent.atomic.AtomicInteger
import scala.util.Try

private[spec] object SessionEnvironmentFixturePlatform {
  private final val Threads = 4
  private final val Timeout = 30.seconds

  def scannedOwners(): Vector[(String, Boolean)] = {
    val config = PluginConfig(Seq("izumi.distage.testkit.spec.sessionplugins"), Nil, cachePackages = true, debug = false, Nil, Nil)
    val firstOwner = new SessionPluginLoader(cache => PluginLoaderDefaultImpl.withPackageCache(cache))
    val secondOwner = new SessionPluginLoader(cache => PluginLoaderDefaultImpl.withPackageCache(cache))
    val first = firstOwner.load(config)
    val repeated = firstOwner.load(config)
    val second = secondOwner.load(config)
    val uncachedFirst = firstOwner.load(config.cachePackages(false))
    val uncachedSecond = firstOwner.load(config.cachePackages(false))
    val all = Vector(first, repeated, second, uncachedFirst, uncachedSecond)
    require(all.forall(_.loaded.size == 1), "Scanning must find exactly the one fixture plugin")
    val plugins = all.map(_.loaded.head).collect { case plugin: SessionScannedPlugin => plugin }
    require(plugins.size == all.size, "Scanning must load the expected fixture plugin class")
    Vector(
      "JVM scanned plugins repeat inside one owner" -> ((first eq repeated) && (plugins(0) eq plugins(1))),
      "JVM independent owners scan distinct plugin objects" -> ((plugins(0) ne plugins(2)) && (plugins(0).provisions ne plugins(2).provisions)),
      "JVM uncached scans are fresh and all providers remain suspended" -> ((plugins(3) ne plugins(4)) && plugins.forall(_.provisions.get() == 0)),
    ) ++ PluginMemoizationFixtures.checks() ++ customDispatch()
  }

  private def customDispatch(): Vector[(String, Boolean)] = {
    val config = PluginConfig(Seq("izumi.distage.testkit.spec.nomatching.dispatch"), Nil, cachePackages = true, debug = false, Nil, Nil)
    Vector(false, true).flatMap {
      mapped =>
        val failure = new IllegalStateException("custom classgraph loading policy")
        val loadCalls = new AtomicInteger(0)
        val loadOwner = new SessionPluginLoader(cache => {
          val loader = new PluginLoaderClassgraphImpl {
            override protected val packageCache: PluginPackageCache = cache
            override def load(config: PluginConfig): LoadedPlugins = {
              val _ = loadCalls.incrementAndGet()
              throw failure
            }
          }
          if (mapped) loader.map(loaded => loaded) else loader
        })
        val rejected = Try(loadOwner.load(config))
        val scanCalls = new AtomicInteger(0)
        val plugin = new SessionScannedPlugin
        val scanOwner = new SessionPluginLoader(cache => {
          val loader = new PluginLoaderClassgraphImpl {
            override protected val packageCache: PluginPackageCache = cache
            override protected def scanClasspath(config: PluginConfig): Seq[PluginBase] = {
              val _ = scanCalls.incrementAndGet()
              Seq(plugin)
            }
          }
          if (mapped) loader.map(loaded => loaded) else loader
        })
        val loaded = scanOwner.load(config)
        Vector(
          ("JVM custom classgraph load retains its original policy mapped=" + mapped) -> (loadCalls.get() == 1 && rejected.failed.toOption.exists(_ eq failure)),
          ("JVM custom classgraph scan retains its original policy mapped=" + mapped) ->
            (scanCalls.get() == 1 && loaded.loaded == Seq(plugin) && plugin.provisions.get() == 0 && plugin.acquired.get() == 0 && plugin.released.get() == 0),
        )
    }
  }

  def concurrent(check: ExecutionContext => Future[Unit]): Unit = {
    val executor = Executors.newFixedThreadPool(Threads)
    val context = ExecutionContext.fromExecutorService(executor)
    try {
      Await.result(check(context), Timeout)
    } finally {
      executor.shutdown()
      require(executor.awaitTermination(Timeout.toMillis, TimeUnit.MILLISECONDS), "Environment fixture executor did not terminate")
    }
    println("SESSION_ENVIRONMENT_FIXTURE_EXECUTOR_TERMINATED")
  }
}
