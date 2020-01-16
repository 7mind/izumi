package izumi.distage.plugins.load

import io.github.classgraph.ClassGraph
import izumi.distage.plugins.{PluginBase, PluginConfig, PluginDef}
import izumi.functional.Value
import izumi.fundamentals.platform.cache.SyncCache

import scala.jdk.CollectionConverters._

class PluginLoaderDefaultImpl
(
  pluginConfig: PluginConfig,
) extends PluginLoader {
  def load(): Seq[PluginBase] = {
    val config = pluginConfig.copy(packagesEnabled = pluginConfig.packagesEnabled)

    val enabledPackages: Seq[String] = config.packagesEnabled.filterNot(config.packagesDisabled.contains)
    val disabledPackages: Seq[String] = config.packagesDisabled

    val pluginBase = classOf[PluginBase]
    val pluginDef = classOf[PluginDef]
    val whitelistedClasses = Seq(pluginDef.getName)

    def loadPkgs(pkgs: Seq[String]): Seq[PluginBase] = {
      PluginLoaderDefaultImpl.doLoad[PluginBase](pluginBase.getName, whitelistedClasses, pkgs, disabledPackages, config.debug)
    }

    if (!pluginConfig.cachePackages) {
      loadPkgs(enabledPackages)
    } else {
      val h1 = scala.util.hashing.MurmurHash3.seqHash(whitelistedClasses)
      val h2 = scala.util.hashing.MurmurHash3.seqHash(disabledPackages)
      enabledPackages.flatMap{
        p =>
          val key = s"$p;$h1;$h2"
          PluginLoaderDefaultImpl.cache.getOrCompute(key, loadPkgs(Seq(p)))
      }
    }
  }
}

object PluginLoaderDefaultImpl {
  private lazy val cache = new SyncCache[String, Seq[PluginBase]]()

  def doLoad[T](base: String, whitelistClasses: Seq[String], enabledPackages: Seq[String], disabledPackages: Seq[String], debug: Boolean): Seq[T] = {
    val scanResult = Value(new ClassGraph())
      .map(_.whitelistPackages(enabledPackages: _*))
      .map(_.whitelistClasses(whitelistClasses :+ base: _*))
      .map(_.blacklistPackages(disabledPackages: _*))
      .map(_.enableClassInfo())
      .map(if (debug) _.verbose() else identity)
      .map(_.scan())
      .get

    try {
      val implementors = scanResult.getClassesImplementing(base)
      implementors
        .asScala
        .filterNot(_.isAbstract)
        .flatMap {
          classInfo =>
            val clz = classInfo.loadClass()

            if (Option(clz.getSimpleName).exists(_.endsWith("$"))) {
              Seq(clz.getField("MODULE$").get(null).asInstanceOf[T])
            } else {
              clz.getDeclaredConstructors.find(_.getParameterCount == 0).map(_.newInstance().asInstanceOf[T]).toSeq
            }
        }
        .toSeq // 2.13 compat
    } finally {
      scanResult.close()
    }
  }
}
