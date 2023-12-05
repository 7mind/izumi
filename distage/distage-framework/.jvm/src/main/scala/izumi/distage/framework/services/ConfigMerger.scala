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
      val nonEmptyShared = shared.filterNot(_.config.isEmpty)
      val roleConfigs = role.flatMap(_.loaded)
      val nonEmptyRole = roleConfigs.filterNot(_.config.isEmpty)

      val toMerge = (shared ++ role.filter(filter).flatMap(_.loaded)).filterNot(_.config.isEmpty)

      val folded = foldConfigs(toMerge)

      val repr = toMerge.map(c => c.clue)

      val sub = logger("config context" -> clue)
      sub.info(s"Config input: ${shared.size -> "shared configs"} of which ${nonEmptyShared.size -> "non empty shared configs"}")
      sub.info(s"Config input: ${roleConfigs.size -> "role configs"}  of which ${nonEmptyRole.size -> "non empty role configs"}")
      sub.info(s"Output config has ${folded.entrySet().size() -> "root nodes"}")
      sub.info(s"${repr.niceList() -> "used configs"}")

      val configRepr = (shared.map(c => (c.clue, true)) ++ role.flatMap(r => r.loaded.map(c => (s"${c.clue}, role=${r.roleConfig.role}", filter(r)))))
        .map(c => s"${c._1}, active = ${c._2}")
      logger.debug(s"Full list of processed configs: ${configRepr.niceList() -> "locations"}")

      folded
    }

    def foldConfigs(roleConfigs: List[ConfigLoadResult.Success]): Config = {
      val fallbackOrdered = roleConfigs.reverse // rightmost config has the highest priority, so we need it to become leftmost

      verifyConfigs(fallbackOrdered)

      fallbackOrdered.foldLeft(ConfigFactory.empty()) {
        case (acc, loaded) =>
          acc.withFallback(loaded.config)
      }
    }

    private def verifyConfigs(fallbackOrdered: List[ConfigLoadResult.Success]): Unit = {
      import izumi.fundamentals.collections.IzCollections.*
      val keyIndex = fallbackOrdered
        .filter(_.src.isInstanceOf[ConfigSource.Resource])
        .flatMap(c => getKeys(c.config).map(key => (key, c)))

      keyIndex.toUniqueMap(identity) match {
        case Left(value) =>
          val diag = value.map { case (key, configs) => s"$key is defined in ${configs.map(_.clue).niceList(prefix = "* ").shift(2)}" }
          logger.warn(s"Reference config resources have ${diag.niceList() -> "conflicting keys"}")
        case Right(_) =>
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
        s"Config with ${config.entrySet().size() -> "root nodes"} has been enhanced with system properties, new config has ${result.entrySet().size() -> "new root nodes"}"
      )

      result
    }
  }
}
