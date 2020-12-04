package izumi.distage.roles

import distage.Injector
import izumi.distage.InjectorFactory
import izumi.distage.config.model.AppConfig
import izumi.distage.framework.services.ConfigLoader
import izumi.distage.model.definition.{BootstrapModule, Id, Module}
import izumi.distage.modules.DefaultModule
import izumi.distage.plugins.load.LoadedPlugins
import izumi.distage.roles.model.meta.RolesInfo
import izumi.fundamentals.platform.functional.Identity
import izumi.reflect.TagK

trait PlanHolder {
  type AppEffectType[_]
  implicit def tagK: TagK[AppEffectType]

  def bsModule: BootstrapModule = BootstrapModule.empty

  def mainAppModule: Module

  def loadConfig(): AppConfig = {
    Injector[Identity]().produceRun(mainAppModule)(identity(_: AppConfig))
  }

  case class Stuff[F[_]](
    // module (planVerifier + config bindings)
    appModule: Module @Id("roleapp"),
    // module (providedKeys + config bindings)
    bsModule: BootstrapModule @Id("roleapp"), // can(should?) verify as well / [append? vs. injectorfactory]
    defaultModule: DefaultModule[F], // can(should?) append it to `appModule` for verifier purposes
    // roots
    rolesInfo: RolesInfo,
    // config load
    configLoader: ConfigLoader,
    // providedKeys
    injectorFactory: InjectorFactory,
    // effectivePlugins
    loadedPlugins: LoadedPlugins,
  )
}

object PlanHolder {
  type Aux[F[_]] = PlanHolder { type AppEffectType[A] = F[A] }
}
