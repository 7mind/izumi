package izumi.distage.testkit.spec

import izumi.distage.plugins.PluginConfig
import izumi.distage.plugins.PluginBase
import izumi.distage.plugins.load.{LoadedPlugins, PluginLoaderClassgraphImpl, PluginLoaderDefaultImpl, PluginPackageCache}
import izumi.distage.testkit.spec.sessionplugins.SessionScannedPlugin
import izumi.fundamentals.platform.language.Quirks.Discarder

import java.util.concurrent.{CyclicBarrier, TimeUnit}
import scala.concurrent.{ExecutionContext, Future}
import java.util.concurrent.atomic.AtomicInteger
import scala.util.Try

private[spec] object SessionEnvironmentFixturePlatform extends ConcurrentSessionEnvironmentFixture {
  def scannedOwners(): Vector[(String, Boolean)] = {
    val config = PluginConfig(Seq("izumi.distage.testkit.spec.sessionplugins"), Nil, cachePackages = true, debug = false, Nil, Nil)
    val firstOwner = new SessionPluginLoader(new PluginPackageCache.Impl, cache => PluginLoaderDefaultImpl.withPackageCache(cache))
    val secondOwner = new SessionPluginLoader(new PluginPackageCache.Impl, cache => PluginLoaderDefaultImpl.withPackageCache(cache))
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
    ) ++ PluginMemoizationFixtures.checks() ++ customDispatch() ++ explicitPackageCache() ++ invocationOwners()
  }

  private def invocationOwners(): Vector[(String, Boolean)] = {
    val config = PluginConfig.cached(Seq("izumi.distage.testkit.spec.sessionplugins"))
    val first = new PluginPackageCache.Impl
    val second = new PluginPackageCache.Impl
    val loader = new PluginLoaderDefaultImpl
    val legacy = loader.load(config).loaded.head
    val owned = loader.withPackageCacheOwner(first).load(config).loaded.head
    val repeated = loader.map(value => value).withPackageCacheOwner(first).load(config).loaded.head
    val other = loader.withPackageCacheOwner(second).load(config).loaded.head
    val failure = new IllegalStateException("custom load after scanning")
    val rejecting = new PluginLoaderDefaultImpl {
      override def load(config: PluginConfig): LoadedPlugins = {
        val loaded = super.load(config)
        if (config.debug) throw failure else loaded
      }
    }
    val rejected = Try(rejecting.withPackageCacheOwner(first).load(config.debug(true)))
    val afterFailure = rejecting.load(config).loaded.head
    var inner = Vector.empty[PluginBase]
    val nested = new PluginLoaderDefaultImpl {
      override def load(config: PluginConfig): LoadedPlugins = {
        if (config.debug) inner :+= withPackageCacheOwner(second).load(config.debug(false)).loaded.head
        super.load(config.debug(false))
      }
    }
    val outer = nested.withPackageCacheOwner(first).load(config.debug(true)).loaded.head
    var parallel = Vector.empty[PluginBase]
    concurrent { context =>
      implicit val ec: ExecutionContext = context
      val owners = Vector(first, second)
      val barrier = new CyclicBarrier(owners.size)
      val shared = new PluginLoaderDefaultImpl {
        override def load(config: PluginConfig): LoadedPlugins = {
          val _ = barrier.await(Timeout.toMillis, TimeUnit.MILLISECONDS)
          super.load(config)
        }
      }
      Future.sequence(owners.map(owner => Future(shared.withPackageCacheOwner(owner).load(config).loaded.head))).map { results => parallel = results }
    }
    Vector(
      "JVM shared loader retains same-owner caching through map" -> ((owned eq repeated) && (owned ne legacy)),
      "JVM shared loader isolates distinct invocation owners" -> (owned ne other),
      "JVM owned custom failure preserves its cause and restores legacy policy" -> (rejected.failed.toOption.exists(_ eq failure) && (afterFailure eq legacy)),
      "JVM nested owned load restores its outer owner" -> ((outer eq owned) && inner.size == 1 && (inner.head eq other)),
      "JVM shared loader isolates two simultaneously entered owned invocations" -> (parallel.size == 2 && (parallel.head eq owned) && (parallel(1) eq other)),
    )
  }

  private def explicitPackageCache(): Vector[(String, Boolean)] = {
    val plugin = new SessionScannedPlugin
    val calls = new AtomicInteger(0)
    val getters = new AtomicInteger(0)
    val explicit = new PluginPackageCache {
      override def getOrCompute(packageName: String, whitelistClasses: Seq[String], excludedPackages: Seq[String])(load: => Seq[PluginBase]): Seq[PluginBase] = {
        val _ = (packageName, whitelistClasses, excludedPackages, () => load)
        calls.incrementAndGet().discard()
        Seq(plugin)
      }
    }
    val owner = new PluginPackageCache {
      override def getOrCompute(packageName: String, whitelistClasses: Seq[String], excludedPackages: Seq[String])(load: => Seq[PluginBase]): Seq[PluginBase] = {
        val _ = (packageName, whitelistClasses, excludedPackages, () => load)
        throw new IllegalStateException("Explicit plugin cache must retain its policy")
      }
    }
    val loader = new PluginLoaderClassgraphImpl {
      override protected def packageCache: PluginPackageCache = { val _ = getters.incrementAndGet(); explicit }
    }
    val request = PluginConfig.empty.enablePackages(Seq("nomatching.first", "nomatching.second")).cachePackages(true).withPackageCacheOwner(owner)
    val loaded = loader.load(request.snapshot())
    val requestPolicy = loaded.loaded == Seq(plugin, plugin) && calls.get() == 2 && getters.get() == 2
    val invoked = loader.withPackageCacheOwner(owner).load(request.snapshot())
    Vector("JVM explicit custom package-cache policy and getter dispatch survive owned requests" -> requestPolicy,
      "JVM explicit custom package-cache policy survives an owned invocation" ->
        (invoked.loaded == Seq(plugin, plugin) && calls.get() == 4 && getters.get() == 4 && plugin.provisions.get() == 0 && plugin.acquired.get() == 0 && plugin.released.get() == 0))
  }

  private def customDispatch(): Vector[(String, Boolean)] = {
    val config = PluginConfig(Seq("izumi.distage.testkit.spec.nomatching.dispatch"), Nil, cachePackages = true, debug = false, Nil, Nil)
    Vector(false, true).flatMap {
      mapped =>
        val failure = new IllegalStateException("custom classgraph loading policy")
        val loadCalls = new AtomicInteger(0)
        val loadOwner = new SessionPluginLoader(new PluginPackageCache.Impl, cache => {
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
        val scanOwner = new SessionPluginLoader(new PluginPackageCache.Impl, cache => {
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

}
