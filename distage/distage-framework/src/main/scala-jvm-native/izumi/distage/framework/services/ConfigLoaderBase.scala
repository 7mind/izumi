package izumi.distage.framework.services

import izumi.distage.config.model.*
import izumi.functional.bio.F
import izumi.fundamentals.platform.exceptions.IzThrowable.*
import izumi.fundamentals.platform.strings.IzString.*
import izumi.logstage.api.IzLogger

private[services] abstract class ConfigLoaderBase(
  logger: IzLogger,
  merger: ConfigMerger,
  configLocation: ConfigLocationProvider,
  configArgs: ConfigLoaderArgs,
) extends ConfigLoader {
  protected def loadConfigSource(isExplicit: Boolean, configSource: ConfigSource): ConfigLoadResult

  /** @throws ConfigLoader.ConfigLoaderException if configuration can't be loaded */
  override def loadConfig(clue: String): AppConfig = {
    val maybeLoadedRoleConfigs = configArgs.configs.map {
      roleConfig =>
        val references = configLocation.forRole(roleConfig.role).map(loadConfigSource(isExplicit = false, _))
        val loaded = roleConfig.configSource match {
          case RoleConfigSource.ConfigFile(file) =>
            Seq(loadConfigSource(isExplicit = true, ConfigSource.File(file))) ++ references
          case RoleConfigSource.ConfigDefault => references
        }
        (roleConfig, loaded)
    }
    val commonExplicit = configArgs.global.map(ConfigSource.File(_)).map(loadConfigSource(isExplicit = true, _))
    val commonReferences = configLocation.commonReferenceConfigs.map(loadConfigSource(isExplicit = false, _))
    val loaded = for {
      shared <- F[Either].traverseAccumErrorsNEList(commonExplicit.toList ++ commonReferences)(_.toEither)
      role <- F[Either].traverseAccumErrors(maybeLoadedRoleConfigs) {
        case (roleConfig, results) =>
          F[Either].traverseAccumErrorsNEList(results)(_.toEither).map(LoadedRoleConfigs(roleConfig, _))
      }
    } yield (shared, role)

    loaded match {
      case Left(errors) =>
        val failures = errors.map(f => s"Failed to load ${f.src} ${f.clue}: ${f.failure.stacktraceString}")
        logger.error(s"Cannot load configuration: ${failures.toList.niceList() -> "failures"}")
        throw new ConfigLoader.ConfigLoaderException(s"Cannot load configuration: failures=${failures.toList.niceList()}", errors.map(_.failure).toList)
      case Right((shared, role)) =>
        val merged = merger.addSystemProps(merger.merge(shared, role, clue))
        AppConfig(merged, shared, role)
    }
  }
}
