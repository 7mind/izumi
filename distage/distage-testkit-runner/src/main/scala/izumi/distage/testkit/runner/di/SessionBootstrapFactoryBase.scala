package izumi.distage.testkit.runner.di

import izumi.distage.config.model.AppConfig
import izumi.distage.framework.config.PlanningOptions
import izumi.distage.framework.model.ActivationInfo
import izumi.distage.framework.services.{ConfigLoader, ModuleProvider}
import izumi.distage.model.definition.Activation
import izumi.distage.roles.launcher.AppShutdownInitiator
import izumi.distage.roles.model.meta.RolesInfo
import izumi.distage.testkit.runner.impl.services.BootstrapFactory
import izumi.fundamentals.platform.cli.model.RoleAppArgs
import izumi.logstage.api.IzLogger
import izumi.logstage.api.logger.LogRouter
import izumi.reflect.TagK

private[di] abstract class SessionBootstrapFactoryBase extends BootstrapFactory {
  override def makeConfigLoader(configBaseName: String, logger: IzLogger): ConfigLoader = BootstrapFactory.Impl.makeConfigLoader(configBaseName, logger)

  override def makeModuleProvider[F[_]: TagK](
    options: PlanningOptions,
    config: AppConfig,
    logRouter: LogRouter,
    roles: RolesInfo,
    activationInfo: ActivationInfo,
    activation: Activation,
  ): ModuleProvider = new ModuleProvider.Impl[F](
    logRouter = logRouter,
    options = options,
    config = config,
    roles = roles,
    args = RoleAppArgs.empty,
    activationInfo = activationInfo,
    shutdownInitiator = AppShutdownInitiator.empty,
    roleAppLocator = None,
    appArtifact = None,
    setupStaticLogRouter = false,
  )
}
