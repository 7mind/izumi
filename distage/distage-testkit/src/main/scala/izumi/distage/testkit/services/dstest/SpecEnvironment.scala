package izumi.distage.testkit.services.dstest

import distage.ModuleBase
import distage.config.AppConfig
import izumi.distage.framework.config.PlanningOptions
import izumi.distage.framework.services.{ConfigLoader, ModuleProvider}
import izumi.distage.model.definition.{Activation, BootstrapModule}
import izumi.distage.roles.meta.RolesInfo
import izumi.distage.roles.model.ActivationInfo
import izumi.fundamentals.platform.cli.model.raw.RawAppArgs
import izumi.fundamentals.reflection.Tags.TagK
import izumi.logstage.api.{IzLogger, Log}

trait SpecEnvironment {
  def bootstrapOverrides: BootstrapModule
  def moduleOverrides: ModuleBase

  def bootstrapLogLevel: Log.Level
  def makeLogger(): IzLogger

  def contextOptions: PlanningOptions

  def makeConfigLoader(logger: IzLogger): ConfigLoader
  def makeModuleProvider(options: PlanningOptions, config: AppConfig, lateLogger: IzLogger, roles: RolesInfo, activationInfo: ActivationInfo, activation: Activation): ModuleProvider
}

object SpecEnvironment {

  class Impl[F[_]: TagK]
  (
    suiteClass: Class[_],
    override val contextOptions: PlanningOptions,
    override val bootstrapOverrides: BootstrapModule,
    override val moduleOverrides: ModuleBase,
    override val bootstrapLogLevel: Log.Level,
  ) extends SpecEnvironment {

    override def makeLogger(): IzLogger = {
      IzLogger(bootstrapLogLevel)("phase" -> "test")
    }

    override def makeConfigLoader(logger: IzLogger): ConfigLoader = {
      val pname = s"${suiteClass.getPackage.getName}"
      val lastPackage = pname.split('.').last
      val classname = suiteClass.getName

      val moreConfigs = Map(
        s"$lastPackage-test" -> None,
        s"$classname-test" -> None,
      )
      new ConfigLoader.LocalFSImpl(logger, None, moreConfigs)
    }

    override def makeModuleProvider(options: PlanningOptions, config: AppConfig, lateLogger: IzLogger, roles: RolesInfo, activationInfo: ActivationInfo, activation: Activation): ModuleProvider = {
      // roles descriptor is not actually required there, we bind it just in case someone wish to inject a class depending on it
      new ModuleProvider.Impl[F](
        logger = lateLogger,
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
