package izumi.distage.framework.services

import com.typesafe.config.{Config, ConfigFactory, ConfigResolveOptions}
import izumi.distage.config.model.*
import izumi.distage.model.definition.Id
import izumi.fundamentals.platform.strings.IzString.*
import izumi.logstage.api.IzLogger

import scala.jdk.CollectionConverters.*
import scala.util.Try

trait ConfigMerger {
  def merge(shared: List[ConfigLoadResult.Success], role: List[LoadedRoleConfigs]): Config
  def mergeFilter(shared: List[ConfigLoadResult.Success], role: List[LoadedRoleConfigs], filter: LoadedRoleConfigs => Boolean): Config
  def foldConfigs(roleConfigs: List[ConfigLoadResult.Success]): Config
  def addSystemProps(config: Config): Config
}

object ConfigMerger {
  class ConfigMergerImpl(logger: IzLogger @Id("early")) extends ConfigMerger {
    override def merge(shared: List[ConfigLoadResult.Success], role: List[LoadedRoleConfigs]): Config = {
      mergeFilter(shared, role, _.roleConfig.active)
    }

    override def mergeFilter(shared: List[ConfigLoadResult.Success], role: List[LoadedRoleConfigs], filter: LoadedRoleConfigs => Boolean): Config = {
      val cfgInfo = (shared ++ role.flatMap(_.loaded)).map(c => c.clue)
      logger.info(s"Using system properties with fallback ${cfgInfo.niceList() -> "config files"}")

      val toMerge = shared ++ role.filter(filter).flatMap(_.loaded)

      foldConfigs(toMerge)
    }

    def foldConfigs(roleConfigs: List[ConfigLoadResult.Success]): Config = {
      roleConfigs.reverse // rightmost config will have the highest priority
        .foldLeft(ConfigFactory.empty()) {
          case (acc, loaded) =>
            verifyConfigs(loaded, acc)
            acc.withFallback(loaded.config)
        }
    }

    protected def verifyConfigs(loaded: ConfigLoadResult.Success, acc: Config): Unit = {
      val duplicateKeys = getKeys(acc) intersect getKeys(loaded.config)
      if (duplicateKeys.nonEmpty) {
        loaded.src match {
          case ConfigSource.Resource(_, ResourceConfigKind.Development) =>
            logger.debug(s"Some keys in supplied ${loaded.src -> "development config"} duplicate already defined keys: ${duplicateKeys.niceList() -> "keys" -> null}")
          case _ =>
            logger.warn(s"Some keys in supplied ${loaded.src -> "config"} duplicate already defined keys: ${duplicateKeys.niceList() -> "keys" -> null}")
        }
      }
    }

    protected def getKeys(c: Config): collection.Set[String] = {
      if (c.isResolved) {
        c.entrySet().asScala.map(_.getKey)
      } else {
        Try {
          c.resolve(ConfigResolveOptions.defaults().setAllowUnresolved(true))
        }.toOption.filter(_.isResolved) match {
          case Some(value) => value.entrySet().asScala.map(_.getKey)
          case None => Set.empty
        }
      }
    }

    override def addSystemProps(config: Config): Config = {
      ConfigFactory
        .systemProperties()
        .withFallback(config)
        .resolve()
    }
  }
}
