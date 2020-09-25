package izumi.distage.testkit.services.dstest

import distage.TagK
import distage.config.AppConfig
import izumi.distage.framework.config.PlanningOptions
import izumi.distage.framework.model.ActivationInfo
import izumi.distage.framework.services.{ConfigLoader, ModuleProvider}
import izumi.distage.model.definition.Activation
import izumi.distage.modules.DefaultModule
import izumi.distage.roles.model.meta.RolesInfo
import izumi.fundamentals.platform.cli.model.raw.RawAppArgs
import izumi.logstage.api.IzLogger
import izumi.logstage.api.logger.LogRouter

trait BootstrapFactory {
  def makeConfigLoader(configResourceName: String, logger: IzLogger): ConfigLoader
  def makeModuleProvider[F[_]: TagK: DefaultModule](
    options: PlanningOptions,
    config: AppConfig,
    logRouter: LogRouter,
    roles: RolesInfo,
    activationInfo: ActivationInfo,
    activation: Activation,
  ): ModuleProvider
}

object BootstrapFactory {
  object Impl extends BootstrapFactory {
    def makeConfigLoader(configResourceName: String, logger: IzLogger): ConfigLoader = {
      new ConfigLoader.LocalFSImpl(logger, ConfigLoader.Args(None, Map(configResourceName -> None), ConfigLoader.defaultBaseConfigs))
    }

    def makeModuleProvider[F[_]: TagK: DefaultModule](
      options: PlanningOptions,
      config: AppConfig,
      logRouter: LogRouter,
      roles: RolesInfo,
      activationInfo: ActivationInfo,
      activation: Activation,
    ): ModuleProvider = {
      // roles descriptor is not actually required there, we bind it just in case someone wish to inject a class depending on it
      new ModuleProvider.Impl(
        logRouter = logRouter,
        config = config,
        roles = roles,
        options = options,
        args = RawAppArgs.empty,
        activationInfo = activationInfo,
        defaultModules = implicitly[DefaultModule[F]],
      )
    }
  }
}
