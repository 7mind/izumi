package izumi.distage.testkit.services.dstest

import distage.config.AppConfig
import distage.{DIKey, ModuleBase}
import izumi.distage.config.ConfigInjectionOptions
import izumi.distage.model.Locator.LocatorRef
import izumi.distage.model.definition.Binding.SingletonBinding
import izumi.distage.model.definition.{BootstrapModule, ImplDef, Module}
import izumi.distage.model.reflection.universe.RuntimeDIUniverse.TagK
import izumi.distage.roles.model.AppActivation
import izumi.distage.roles.model.meta.RolesInfo
import izumi.distage.roles.config.ContextOptions
import izumi.distage.roles.services.ResourceRewriter.RewriteRules
import izumi.distage.roles.services.{ConfigLoader, ModuleProvider}
import izumi.fundamentals.platform.cli.model.raw.RawAppArgs
import izumi.fundamentals.platform.language.CodePositionMaterializer
import izumi.logstage.api.IzLogger
import izumi.logstage.api.Log.Level

class DistageTestEnvironmentImpl[F[_] : TagK](suiteClass: Class[_]) extends DistageTestEnvironment[F] {
  /** Override this to disable instantiation of fixture parameters that aren't bound in `makeBindings` */
  def addUnboundParametersAsRoots(roots: Set[DIKey], primaryModule: ModuleBase): ModuleBase = {
    val paramsModule = Module.make {
      (roots - DIKey.get[LocatorRef])
        .filterNot(_.tpe.use(_.typeSymbol.isAbstract))
        .map {
          key =>
            SingletonBinding(key, ImplDef.TypeImpl(key.tpe), Set.empty, CodePositionMaterializer().get.position)
        }
    }

    paramsModule overridenBy primaryModule
  }

  def bootstrapOverride: BootstrapModule = BootstrapModule.empty

  def appOverride: ModuleBase = Module.empty

  def bootstrapLogLevel: Level = IzLogger.Level.Info

  def makeLogger(): IzLogger = IzLogger.apply(bootstrapLogLevel)("phase" -> "test")

  def contextOptions(): ContextOptions = {
    ContextOptions(
      addGvDump = false,
      warnOnCircularDeps = true,
      RewriteRules(),
      ConfigInjectionOptions(),
    )
  }

  def makeConfigLoader(logger: IzLogger): ConfigLoader = {
    val pname = s"${suiteClass.getPackage.getName}"
    val lastPackage = pname.split('.').last
    val classname = suiteClass.getName

    val moreConfigs = Map(
      s"$lastPackage-test" -> None,
      s"$classname-test" -> None,
    )
    new ConfigLoader.LocalFSImpl(logger, None, moreConfigs)
  }

  def makeModuleProvider(options: ContextOptions, config: AppConfig, lateLogger: IzLogger, roles: RolesInfo, activation: AppActivation): ModuleProvider[F] = {
    // roles descriptor is not actually required there, we bind it just in case someone wish to inject a class depending on it
    new ModuleProvider.Impl[F](
      lateLogger,
      config,
      roles,
      options,
      RawAppArgs.empty,
      activation,
    )
  }
}
