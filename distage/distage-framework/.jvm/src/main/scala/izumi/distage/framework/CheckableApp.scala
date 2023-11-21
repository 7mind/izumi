package izumi.distage.framework

import com.typesafe.config.ConfigFactory
import distage.Injector
import izumi.distage.InjectorFactory
import izumi.distage.config.model.AppConfig
import izumi.distage.config.model.exceptions.DIConfigReadException
import izumi.distage.constructors.TraitConstructor
import izumi.distage.framework.PlanCheck.runtime.RoleSelection
import izumi.distage.framework.model.PlanCheckInput
import izumi.distage.framework.services.ConfigLoader
import izumi.distage.model.definition.{Binding, BootstrapModule, Id, Module, ModuleBase, ModuleDef, impl}
import izumi.distage.model.plan.Roots
import izumi.distage.model.providers.Functoid
import izumi.distage.model.reflection.SafeType
import izumi.distage.modules.DefaultModule
import izumi.distage.plugins.load.LoadedPlugins
import izumi.distage.roles.launcher.RoleProvider
import izumi.distage.roles.model.meta.{RoleBinding, RolesInfo}
import izumi.fundamentals.platform.cli.model.raw.RawAppArgs
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.language.Quirks.Discarder
import izumi.logstage.api.IzLogger
import izumi.reflect.TagK

/**
  * Represents `distage` apps that can be checked by [[izumi.distage.framework.PlanCheck]] at compile-time.
  * `CheckableApp` must be inherited in a top-level object to be usable with `PlanCheck`
  *
  * [[izumi.distage.roles.RoleAppMain Role-based applications]] automatically inherit from [[RoleCheckableApp]]
  * and can be checked out of the box.
  *
  * Apps assembled directly using `distage-core`'s `distage.Injector` will need to implement the interface manually,
  * using [[CoreCheckableApp]] or [[CoreCheckableAppSimple]]
  *
  * @see [[izumi.distage.framework.PlanCheck]]
  */
trait CheckableApp {
  type AppEffectType[_]
  def tagK: TagK[AppEffectType]

  def preparePlanCheckInput(
    selectedRoles: RoleSelection,
    chosenConfigFile: Option[String],
  ): PlanCheckInput[AppEffectType]
}
object CheckableApp {
  type Aux[F[_]] = CheckableApp { type AppEffectType[A] = F[A] }
}

abstract class CoreCheckableApp[F[_]](implicit val tagK: TagK[F]) extends CheckableApp {
  override final type AppEffectType[A] = F[A]
}

abstract class CoreCheckableAppSimple[F[_]: TagK: DefaultModule] extends CoreCheckableApp[F] {
  def module: ModuleBase
  def roots: Roots

  override final def preparePlanCheckInput(selectedRoles: RoleSelection, chosenConfigFile: Option[String]): PlanCheckInput[AppEffectType] = {
    PlanCheckInput.noConfig(module, roots)
  }
}

abstract class RoleCheckableApp[F[_]](override implicit val tagK: TagK[F]) extends CheckableApp {
  def roleAppBootModule: Module

  override final type AppEffectType[A] = F[A]

  override def preparePlanCheckInput(
    selectedRoles: RoleSelection,
    chosenConfigFile: Option[String],
  ): PlanCheckInput[F] = {
    val baseModuleOverrides = roleAppBootModulePlanCheckOverrides(selectedRoles, chosenConfigFile.map(this.getClass.getClassLoader -> _))
    val baseModuleWithOverrides = this.roleAppBootModule.overriddenBy(baseModuleOverrides)

    Injector[Identity]().produceRun(baseModuleWithOverrides)(Functoid {
      (
        // module
        bsModule: BootstrapModule @Id("roleapp"),
        appModule: Module @Id("roleapp"),
        defaultModule: DefaultModule[F],
        // roots
        rolesInfo: RolesInfo,
        // config
        configLoader: ConfigLoader,
        // providedKeys
        injectorFactory: InjectorFactory,
        // effectivePlugins
        appPlugins: LoadedPlugins @Id("main"),
        bsPlugins: LoadedPlugins @Id("bootstrap"),
      ) =>
        val defaultModuleBindings = defaultModule.module.bindings

        PlanCheckInput(
          effectType = tagK,
          module = ModuleBase.make(
            ModuleBase
              .overrideImpl(
                ModuleBase.overrideImpl(bsModule.iterator, defaultModuleBindings.iterator),
                appModule.iterator,
              )
              .toSet
          ),
          roots = Roots(
            // bootstrap is produced with Roots.Everything, so each bootstrap component is effectively a root
            bsModule.keys ++
            rolesInfo.requiredComponents
          ),
          roleNames = rolesInfo.requiredRoleNames,
          providedKeys = injectorFactory.providedKeys[F](bsModule)(DefaultModule[F](Module.make(defaultModuleBindings))),
          configLoader = configLoader,
          appPlugins = appPlugins,
          bsPlugins = bsPlugins,
        )
    })
  }

  protected[this] final def roleAppBootModulePlanCheckOverrides(
    chosenRoles: RoleSelection,
    chosenConfigResource: Option[(ClassLoader, String)],
  ): ModuleDef = {
    new ModuleDef {
      make[IzLogger].named("early").fromValue(IzLogger.NullLogger)
      make[IzLogger].fromValue(IzLogger.NullLogger)

      make[AppConfig].fromValue(AppConfig.empty)
      make[RawAppArgs].fromValue(RawAppArgs.empty)

      make[RoleProvider].from {
        chosenRoles match {
          case RoleSelection.Everything =>
            namePredicateRoleProvider(_ => true)

          case RoleSelection.AllExcluding(excluded) =>
            namePredicateRoleProvider(!excluded(_))

          case RoleSelection.OnlySelected(selection) =>
            @impl trait SelectedRoleProvider extends RoleProvider.ReflectiveImpl {
              override protected def getInfo(bindings: Set[Binding], requiredRoles: Set[String], roleType: SafeType): RolesInfo = {
                requiredRoles.discard()
                super.getInfo(bindings, selection, roleType)
              }
            }
            TraitConstructor[SelectedRoleProvider]
        }
      }

      chosenConfigResource match {
        case Some((classLoader, resourceName)) =>
          make[ConfigLoader].fromValue(specificResourceConfigLoader(classLoader, resourceName))
        case None =>
        // keep original ConfigLoader
      }

      private[this] def namePredicateRoleProvider(f: String => Boolean): Functoid[RoleProvider] = {
        // use Auto-Traits feature to override just the few specific methods of a class succinctly
        @impl trait NamePredicateRoleProvider extends RoleProvider.ReflectiveImpl {
          override protected def isRoleEnabled(requiredRoles: Set[String])(b: RoleBinding): Boolean = {
            f(b.descriptor.id)
          }
          override protected def getInfo(bindings: Set[Binding], requiredRoles: Set[String], roleType: SafeType): RolesInfo = {
            requiredRoles.discard()
            super.getInfo(bindings, Set.empty, roleType)
          }
        }

        TraitConstructor[NamePredicateRoleProvider]
      }

      private[this] def specificResourceConfigLoader(classLoader: ClassLoader, resourceName: String): ConfigLoader = {
        () =>
          val cfg = ConfigFactory.parseResources(classLoader, resourceName).resolve()
          if (cfg.origin().resource() eq null) {
            throw new DIConfigReadException(s"Couldn't find a config resource with name `$resourceName` - file not found", null)
          }
          AppConfig(cfg)
      }
    }
  }

}
