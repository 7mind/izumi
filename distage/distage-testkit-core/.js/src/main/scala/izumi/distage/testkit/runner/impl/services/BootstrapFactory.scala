package izumi.distage.testkit.runner.impl.services

import distage.config.AppConfig
import izumi.distage.framework.config.PlanningOptions
import izumi.distage.framework.model.ActivationInfo
import izumi.distage.framework.services.{ConfigLoader, ModuleProvider}
import izumi.distage.model.definition.Activation
import izumi.distage.roles.DebugProperties
import izumi.distage.roles.launcher.AppShutdownInitiator
import izumi.distage.roles.model.meta.RolesInfo
import izumi.fundamentals.platform.cli.model.RoleAppArgs
import izumi.logstage.api.IzLogger
import izumi.logstage.api.logger.LogRouter
import izumi.reflect.TagK

/**
  * The purpose of this class is to allow testkit user to override
  * module loading and config loading logic by overriding [[izumi.distage.testkit.model.TestConfig.bootstrapFactory]]
  */
trait BootstrapFactory {
  def makeModuleProvider[F[_]: TagK](
    options: PlanningOptions,
    config: AppConfig,
    logRouter: LogRouter,
    roles: RolesInfo,
    activationInfo: ActivationInfo,
    activation: Activation,
  ): ModuleProvider

  def makeConfigLoader(configBaseName: String, logger: IzLogger): ConfigLoader
}

object BootstrapFactory {
  object Impl extends BootstrapFactory {
    override def makeConfigLoader(configBaseName: String, logger: IzLogger): ConfigLoader = {
      // On Scala.js, we don't have file system access, so we use an empty config loader
      // Users can provide config via TestConfig.configOverrides
      ConfigLoader.empty
    }

    override def makeModuleProvider[F[_]: TagK](
      options: PlanningOptions,
      config: AppConfig,
      logRouter: LogRouter,
      roles: RolesInfo,
      activationInfo: ActivationInfo,
      activation: Activation,
    ): ModuleProvider = {
      // roles descriptor is not actually required there, we bind it just in case someone wish to inject a class depending on it
      new ModuleProvider.Impl[F](
        logRouter = logRouter,
        options = options,
        config = config,
        roles = roles,
        args = RoleAppArgs.empty,
        activationInfo = activationInfo,
        shutdownInitiator = AppShutdownInitiator.empty,
        roleAppLocator = None,
        appArtifact = None,
        setupStaticLogRouter = DebugProperties.`izumi.distage.roles.logs.static-log-router`.boolValue(true),
      )
    }
  }
}
