package izumi.distage.testkit.services.dstest

import distage.TagK
import distage.config.AppConfig
import izumi.distage.framework.config.PlanningOptions
import izumi.distage.framework.model.ActivationInfo
import izumi.distage.framework.services.{ConfigLoader, ModuleProvider}
import izumi.distage.model.definition.Activation
import izumi.distage.roles.model.meta.RolesInfo
import izumi.fundamentals.platform.cli.model.raw.RawAppArgs
import izumi.logstage.api.IzLogger
import izumi.logstage.api.logger.LogRouter

trait BootstrapFactory {
  def makeConfigLoader(suiteClass: String, logger: IzLogger): ConfigLoader
  def makeModuleProvider[F[_]: TagK](options: PlanningOptions, config: AppConfig, logRouter: LogRouter, roles: RolesInfo, activationInfo: ActivationInfo, activation: Activation): ModuleProvider
}

object BootstrapFactory {
  object Impl extends BootstrapFactory {
    def makeConfigLoader(configPackage: String, logger: IzLogger): ConfigLoader = {
      val lastPackage = configPackage.split('.').last

      val moreConfigs = Map(
        s"$lastPackage-test" -> None,
      )
      new ConfigLoader.LocalFSImpl(logger, None, moreConfigs)
    }

    def makeModuleProvider[F[_]: TagK](options: PlanningOptions, config: AppConfig, logRouter: LogRouter, roles: RolesInfo, activationInfo: ActivationInfo, activation: Activation): ModuleProvider = {
      // roles descriptor is not actually required there, we bind it just in case someone wish to inject a class depending on it
      new ModuleProvider.Impl[F](
        logRouter = logRouter,
        config = config,
        roles = roles,
        options = options,
        args = RawAppArgs.empty,
        activationInfo = activationInfo,
        activation = activation,
      )
    }
  }
}
