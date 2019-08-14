package izumi.distage.testkit.services.dstest

import izumi.distage.model.definition.BootstrapModule
import izumi.distage.roles.model.AppActivation
import izumi.distage.roles.model.meta.RolesInfo
import izumi.distage.roles.services.{ConfigLoader, ModuleProvider, ModuleProviderImpl}
import izumi.logstage.api.{IzLogger, Log}
import distage.config.AppConfig
import distage.{DIKey, ModuleBase}

trait DistageTestEnvironment[F[_]] {

  /** Override this to disable instantiation of fixture parameters that aren't bound in `makeBindings` */
  def addUnboundParametersAsRoots(roots: Set[DIKey], primaryModule: ModuleBase): ModuleBase

  def bootstrapOverride: BootstrapModule

  def appOverride: ModuleBase

  def bootstrapLogLevel: Log.Level

  def makeLogger(): IzLogger

  def contextOptions(): ModuleProviderImpl.ContextOptions

  def makeConfigLoader(logger: IzLogger): ConfigLoader

  def makeModuleProvider(options: ModuleProviderImpl.ContextOptions, config: AppConfig, lateLogger: IzLogger, roles: RolesInfo, activation: AppActivation): ModuleProvider[F]
}
