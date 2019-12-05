package izumi.distage.testkit.services.dstest

import distage.config.AppConfig
import distage.{DIKey, ModuleBase}
import izumi.distage.model.definition.BootstrapModule
import izumi.distage.roles.model.AppActivation
import izumi.distage.roles.config.ContextOptions
import izumi.distage.roles.meta.RolesInfo
import izumi.distage.roles.services.{ConfigLoader, ModuleProvider}
import izumi.logstage.api.{IzLogger, Log}

trait SpecEnvironment[F[_]] {

  def bootstrapOverrides: BootstrapModule
  def moduleOverrides: ModuleBase

  def bootstrapLogLevel: Log.Level
  def makeLogger(): IzLogger

  def contextOptions: ContextOptions

  /** Override this to disable instantiation of fixture parameters that aren't bound in `makeBindings` */
  def addUnboundParametersAsRoots(roots: Set[DIKey], primaryModule: ModuleBase): ModuleBase
  def makeConfigLoader(logger: IzLogger): ConfigLoader
  def makeModuleProvider(options: ContextOptions, config: AppConfig, lateLogger: IzLogger, roles: RolesInfo, activation: AppActivation): ModuleProvider[F]
}
