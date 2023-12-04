package izumi.distage.framework.services

import com.typesafe.config.{Config, ConfigFactory, ConfigResolveOptions}
import izumi.distage.config.model.*
import izumi.distage.model.definition.Id
import izumi.fundamentals.platform.strings.IzString.*
import izumi.logstage.api.IzLogger

import scala.jdk.CollectionConverters.*
import scala.util.Try

trait ConfigMerger {
  def merge(shared: List[ConfigLoadResult.Success], role: List[LoadedRoleConfigs], clue: String): Config
  def mergeFilter(shared: List[ConfigLoadResult.Success], role: List[LoadedRoleConfigs], filter: LoadedRoleConfigs => Boolean, clue: String): Config
  def foldConfigs(roleConfigs: List[ConfigLoadResult.Success]): Config
  def addSystemProps(config: Config): Config
}

object ConfigMerger {
  class ConfigMergerImpl(logger: IzLogger @Id("early")) extends ConfigMerger {
    override def merge(shared: List[ConfigLoadResult.Success], role: List[LoadedRoleConfigs], clue: String): Config = {
      mergeFilter(shared, role, _.roleConfig.active, clue)
    }

    override def mergeFilter(shared: List[ConfigLoadResult.Success], role: List[LoadedRoleConfigs], filter: LoadedRoleConfigs => Boolean, clue: String): Config = {
      val cfgInfo = shared ++ role.flatMap(_.loaded)
      val nonEmpty = cfgInfo.filterNot(_.config.isEmpty)

      val toMerge = shared ++ role.filter(filter).flatMap(_.loaded)

      val folded = foldConfigs(toMerge)
      logger.info(
        s"${clue -> "context"}: merged ${shared.size -> "shared configs"} and ${role.size -> "role configs"} of which ${nonEmpty.length -> "non empty"} into config with ${folded
            .entrySet().size() -> "root nodes"}, ${nonEmpty
            .map(c => c.clue).niceList() -> "config files"}"
      )
      logger.trace(s"Full list of processed configs: ${cfgInfo.map(c => c.clue).niceList() -> "locations"}")

      folded
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
      val result = ConfigFactory
        .systemProperties()
        .withFallback(config)
        .resolve()
      logger.info(
        s"Config with ${config.entrySet().size() -> "root nodes"} had been enhanced with system properties, new config has ${result.entrySet().size() -> "new root nodes"}"
      )

      result
    }
  }
}
