package izumi.distage.testkit.runner.di

import izumi.distage.plugins.load.{PluginLoader, PluginLoaderDefaultImpl, PluginLoaderFactory, PluginPackageCache}
import izumi.distage.testkit.spec.SessionPluginLoader
import izumi.fundamentals.platform.cache.SyncCache

import scala.util.control.NonFatal

private[di] final class SessionPluginLoaders {
  import SessionPluginLoaders.FactoryIdentity
  private val packageCache: PluginPackageCache = new PluginPackageCache.Impl
  private val factories = new SyncCache[FactoryIdentity, Materialization]
  val defaultFactory: PluginLoaderFactory = new PluginLoaderFactory {
    override def create(cache: PluginPackageCache): PluginLoader = new SessionPluginLoader(cache, owner => PluginLoaderDefaultImpl.withPackageCache(owner))
  }

  def load(factory: PluginLoaderFactory): PluginLoader = {
    factories.getOrCompute(new FactoryIdentity(factory), new Materialization(factory)).load()
  }

  private final class Materialization(factory: PluginLoaderFactory) {
    private var creating = false
    private lazy val result: Either[Throwable, PluginLoader] = {
      try Right(factory.create(packageCache))
      catch { case NonFatal(cause) => Left(cause) }
    }

    def load(): PluginLoader = synchronized {
      require(!creating, "Recursive plugin loader factory materialization")
      creating = true
      try result.fold(cause => throw cause, loader => loader)
      finally { creating = false }
    }
  }
}

private[di] object SessionPluginLoaders {
  private final class FactoryIdentity(val value: PluginLoaderFactory) {
    override def equals(other: Any): Boolean = other match {
      case identity: FactoryIdentity => value eq identity.value
      case _ => false
    }
    override def hashCode(): Int = System.identityHashCode(value)
  }
}
