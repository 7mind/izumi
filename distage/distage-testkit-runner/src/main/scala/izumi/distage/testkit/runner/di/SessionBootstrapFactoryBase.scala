package izumi.distage.testkit.runner.di

import izumi.distage.config.model.AppConfig
import izumi.distage.framework.config.PlanningOptions
import izumi.distage.framework.model.ActivationInfo
import izumi.distage.framework.services.{ConfigLoader, ModuleProvider}
import izumi.distage.model.definition.Activation
import izumi.distage.roles.model.meta.RolesInfo
import izumi.distage.testkit.runner.impl.services.BootstrapFactory
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
  ): ModuleProvider = BootstrapFactory.Impl.makeModuleProvider[F](options, config, logRouter, roles, activationInfo, activation)
}
