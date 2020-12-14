package izumi.distage.framework.model

import distage.Injector
import izumi.distage.framework.services.ConfigLoader
import izumi.distage.framework.services.ConfigLoader.ConfigLocation
import izumi.distage.model.definition.ModuleBase
import izumi.distage.model.plan.Roots
import izumi.distage.model.reflection.DIKey
import izumi.distage.modules.DefaultModule
import izumi.distage.plugins.load.LoadedPlugins
import izumi.logstage.api.IzLogger
import izumi.reflect.TagK

final case class PlanCheckInput[F[_]](
  effectType: TagK[F],
  module: ModuleBase,
  roots: Roots,
  providedKeys: Set[DIKey],
  configLoader: ConfigLoader,
  appPlugins: LoadedPlugins,
  bsPlugins: LoadedPlugins,
)
object PlanCheckInput {
  def apply[F[_]](
    module: ModuleBase,
    roots: Roots,
    configLoader: ConfigLoader = new ConfigLoader.LocalFSImpl(IzLogger(), ConfigLocation.Default, ConfigLoader.Args.empty),
    appPlugins: LoadedPlugins = LoadedPlugins.empty,
    bsPlugins: LoadedPlugins = LoadedPlugins.empty,
  )(implicit effectType: TagK[F],
    defaultModule: DefaultModule[F],
  ): PlanCheckInput[F] = PlanCheckInput(
    effectType = effectType,
    module = module,
    roots = roots,
    providedKeys = Injector.providedKeys[F]()(defaultModule),
    configLoader = configLoader,
    appPlugins = appPlugins,
    bsPlugins = bsPlugins,
  )

  /**
    * Provide empty config for the purpose of checking config bindings ([[izumi.distage.config.ConfigModuleDef]])
    *
    * If the app uses config bindings and disables loading, [[izumi.distage.framework.PlanCheckConfig#checkConfig]]
    * should be set to `false` for `PlanCheck` to pass
    */
  def noConfig[F[_]](
    module: ModuleBase,
    roots: Roots,
    appPlugins: LoadedPlugins = LoadedPlugins.empty,
    bsPlugins: LoadedPlugins = LoadedPlugins.empty,
  )(implicit effectType: TagK[F],
    defaultModule: DefaultModule[F],
  ): PlanCheckInput[F] = PlanCheckInput(
    module = module,
    roots = roots,
    configLoader = ConfigLoader.empty,
    appPlugins = appPlugins,
    bsPlugins = bsPlugins,
  )
}
