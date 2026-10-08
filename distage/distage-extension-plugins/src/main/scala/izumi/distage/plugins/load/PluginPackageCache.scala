package izumi.distage.plugins.load

import izumi.distage.plugins.PluginBase
import izumi.fundamentals.platform.cache.SyncCache

trait PluginPackageCache {
  def getOrCompute(packageName: String, whitelistClasses: Seq[String], excludedPackages: Seq[String])(load: => Seq[PluginBase]): Seq[PluginBase]
}

object PluginPackageCache {
  final class Impl extends PluginPackageCache {
    private val cache = new SyncCache[Key, Seq[PluginBase]]

    override def getOrCompute(packageName: String, whitelistClasses: Seq[String], excludedPackages: Seq[String])(load: => Seq[PluginBase]): Seq[PluginBase] = {
      cache.getOrCompute(Key(packageName, whitelistClasses.toVector, excludedPackages.toVector), load)
    }
  }

  private final case class Key(packageName: String, whitelistClasses: Vector[String], excludedPackages: Vector[String])
}
