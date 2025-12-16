package izumi.distage.framework.model

import distage.Injector
import izumi.distage.framework.services.ConfigLoader
import izumi.distage.model.definition.ModuleBase
import izumi.distage.model.plan.Roots
import izumi.distage.model.reflection.DIKey
import izumi.distage.modules.DefaultModule
import izumi.distage.plugins.load.LoadedPlugins
import izumi.reflect.TagK

final case class PlanCheckInput[F[_]](
  effectType: TagK[F],
  module: ModuleBase,
  roots: Roots,
  roleNames: Set[String],
  providedKeys: Set[DIKey],
  configLoader: ConfigLoader,
  appPlugins: LoadedPlugins,
  bsPlugins: LoadedPlugins,
)
object PlanCheckInput {
  def withConfigLoader[F[_]](
    module: ModuleBase,
    roots: Roots,
    configLoader: ConfigLoader,
    roleNames: Set[String] = Set.empty,
    appPlugins: LoadedPlugins = LoadedPlugins.empty,
    bsPlugins: LoadedPlugins = LoadedPlugins.empty,
  )(implicit effectType: TagK[F],
    defaultModule: DefaultModule[F],
  ): PlanCheckInput[F] = PlanCheckInput(
    effectType = effectType,
    module = module,
    roots = roots,
    roleNames = roleNames,
    providedKeys = Injector.providedKeys[F]()(using defaultModule),
    configLoader = configLoader,
    appPlugins = appPlugins,
    bsPlugins = bsPlugins,
  )

  /**
    * Provide empty config for the purpose of checking config bindings ([[izumi.distage.config.ConfigModuleDef]])
    *
    * If the app uses config bindings but uses [[noConfig]], [[izumi.distage.framework.PlanCheckConfig#checkConfig]]
    * should be set to `false` for `PlanCheck` to pass
    */
  def noConfig[F[_]](
    module: ModuleBase,
    roots: Roots,
    roleNames: Set[String] = Set.empty,
    appPlugins: LoadedPlugins = LoadedPlugins.empty,
    bsPlugins: LoadedPlugins = LoadedPlugins.empty,
  )(implicit effectType: TagK[F],
    defaultModule: DefaultModule[F],
  ): PlanCheckInput[F] = PlanCheckInput(
    effectType = effectType,
    module = module,
    roots = roots,
    roleNames = roleNames,
    providedKeys = Injector.providedKeys[F]()(using defaultModule),
    configLoader = ConfigLoader.empty,
    appPlugins = appPlugins,
    bsPlugins = bsPlugins,
  )
}
