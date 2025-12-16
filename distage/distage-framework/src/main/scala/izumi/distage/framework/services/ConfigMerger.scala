package izumi.distage.framework.services

import izumi.distage.config.DistageConfigImpl
import izumi.distage.config.model.*
import izumi.distage.model.definition.Id
import izumi.fundamentals.platform.strings.IzString.*
import izumi.logstage.api.IzLogger

import scala.util.Try

trait ConfigMerger {
  def merge(shared: List[ConfigLoadResult.Success], role: List[LoadedRoleConfigs], clue: String): DistageConfigImpl
  def mergeFilter(
    logger: IzLogger,
    filteringStrategy: ConfigFilteringStrategy,
    filter: LoadedRoleConfigs => Boolean,
  )(shared: List[ConfigLoadResult.Success],
    role: List[LoadedRoleConfigs],
    clue: String,
  ): DistageConfigImpl

  def foldConfigs(roleConfigs: List[ConfigLoadResult.Success]): DistageConfigImpl
  def addSystemProps(config: DistageConfigImpl): DistageConfigImpl
}

object ConfigMerger {
  class ConfigMergerImpl(
    logger: IzLogger @Id("early"),
    enableConfigEnvOverrides: Boolean @Id("distage.roles.enable-config-environment-overrides"),
    filteringStrategy: ConfigFilteringStrategy,
  ) extends ConfigMerger
    with ConfigMergerPlatformSpecific {

    override def merge(shared: List[ConfigLoadResult.Success], role: List[LoadedRoleConfigs], clue: String): DistageConfigImpl = {
      mergeFilter(logger, filteringStrategy, _.roleConfig.active)(shared, role, clue)
    }

    override def mergeFilter(
      logger: IzLogger,
      filteringStrategy: ConfigFilteringStrategy,
      filter: LoadedRoleConfigs => Boolean,
    )(shared0: List[ConfigLoadResult.Success],
      role0: List[LoadedRoleConfigs],
      clue: String,
    ): DistageConfigImpl = {
      val shared = filteringStrategy.filterSharedConfigs(shared0)
      val role = filteringStrategy.filterRoleConfigs(role0)

      val nonEmptyShared = shared.filterNot(_.config.isEmpty)
      val roleConfigs = role.flatMap(_.loaded)
      val nonEmptyRole = roleConfigs.filterNot(_.config.isEmpty)

      val toMerge = (role.filter(filter).flatMap(_.loaded) ++ shared).filterNot(_.config.isEmpty)

      val folded = foldConfigs(toMerge)

      val sub = logger("config context" -> clue)
      sub.info(s"Config input: ${shared.size -> "shared configs"} of which ${nonEmptyShared.size -> "non empty shared configs"}")
      sub.info(s"Config input: ${roleConfigs.size -> "role configs"}  of which ${nonEmptyRole.size -> "non empty role configs"}")
      sub.info(s"Output config has ${DistageConfigImpl.allKeys(folded).size -> "keys"}")
      sub.info(s"The following configs were used (highest priority first): ${toMerge.map(_.clue).niceList() -> "used configs"}")

      def configRepr(): List[String] = (shared.map(c => (c.clue, true))
        ++ role.flatMap(r => r.loaded.map(c => (s"${c.clue}, role=${r.roleConfig.role}", filter(r)))))
        .map(c => s"${c._1}, active = ${c._2}")

      logger.debug(s"Full list of processed configs: ${configRepr().niceList() -> "locations"}")

      folded
    }

    def foldConfigs(roleConfigs: List[ConfigLoadResult.Success]): DistageConfigImpl = {
      verifyConfigs(roleConfigs)

      roleConfigs.foldLeft(DistageConfigImpl.empty) {
        case (acc, loaded) =>
          DistageConfigImpl.withFallback(acc, loaded.config)
      }
    }

    private def verifyConfigs(fallbackOrdered: List[ConfigLoadResult.Success]): Unit = {
      import izumi.fundamentals.collections.IzCollections.*
      val keyIndex = fallbackOrdered
        .filter(_.src.isInstanceOf[ConfigSource.Resource])
        .flatMap(c => getKeys(c.config).map(key => (key, c)))

      keyIndex.toUniqueMap(identity) match {
        case Left(dups) =>
          val diag = dups.map { case (key, configs) => s"$key is defined in ${configs.map(_.clue).niceList(prefix = "* ").shift(2)}" }
          logger.warn(s"Reference config resources have ${diag.niceList() -> "conflicting keys"}")
        case Right(_) =>
      }
    }

    protected def getKeys(config: DistageConfigImpl): collection.Set[String] = {
      if (DistageConfigImpl.isResolved(config)) {
        DistageConfigImpl.allKeys(config)
      } else {
        Try {
          DistageConfigImpl.resolveAllowUnresolved(config)
        }.toOption.filter(DistageConfigImpl.isResolved) match {
          case Some(value) =>
            DistageConfigImpl.allKeys(value)
          case None =>
            Set.empty
        }
      }
    }

    override def addSystemProps(config: DistageConfigImpl): DistageConfigImpl = {
      addSystemPropsImpl(config, enableConfigEnvOverrides, logger)
    }
  }
}
