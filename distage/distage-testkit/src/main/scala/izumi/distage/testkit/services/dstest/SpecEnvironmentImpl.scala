package izumi.distage.testkit.services.dstest

import distage.config.AppConfig
import distage.{DIKey, ModuleBase}
import izumi.distage.model.definition.BootstrapModule
import izumi.distage.roles.config.ContextOptions
import izumi.distage.roles.model.AppActivation
import izumi.distage.roles.model.meta.RolesInfo
import izumi.distage.roles.services.{ConfigLoader, ModuleProvider}
import izumi.fundamentals.platform.cli.model.raw.RawAppArgs
import izumi.fundamentals.reflection.Tags.TagK
import izumi.logstage.api.{IzLogger, Log}

class SpecEnvironmentImpl[F[_]: TagK]
(
  suiteClass: Class[_],
  override val contextOptions: ContextOptions,
  override val bootstrapOverrides: BootstrapModule,
  override val moduleOverrides: ModuleBase,
  override val bootstrapLogLevel: Log.Level,
) extends SpecEnvironment[F] {

  /** Override this to disable instantiation of fixture parameters that aren't bound in `makeBindings` */
  def addUnboundParametersAsRoots(roots: Set[DIKey], primaryModule: ModuleBase): ModuleBase = {
    // FIXME: can't add unbound parameters anymore ???
    // ???
    /*val paramsModule = Module.make {
      (roots - DIKey.get[LocatorRef])
        .filterNot(_.tpe.use(_.typeSymbol.isAbstract))
        .map {
          key =>
            SingletonBinding(key, ImplDef.TypeImpl(key.tpe), Set.empty, CodePositionMaterializer().get.position)
        }
    }

    paramsModule overridenBy */primaryModule
  }

  def makeLogger(): IzLogger = {
    IzLogger(bootstrapLogLevel)("phase" -> "test")
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
      logger = lateLogger,
      config = config,
      roles = roles,
      options = options,
      args = RawAppArgs.empty,
      activation = activation,
    )
  }
}
