package izumi.distage.framework

import distage.Injector
import izumi.distage.InjectorFactory
import izumi.distage.config.model.AppConfig
import izumi.distage.constructors.TraitConstructor
import izumi.distage.framework.PlanCheck.RoleSelection
import izumi.distage.framework.model.PlanCheckInput
import izumi.distage.framework.services.ConfigLoader
import izumi.distage.model.definition.{Binding, BootstrapModule, Id, Module, ModuleBase, ModuleDef, impl}
import izumi.distage.model.plan.Roots
import izumi.distage.model.planning.AxisPoint
import izumi.distage.model.providers.Functoid
import izumi.distage.model.reflection.SafeType
import izumi.distage.modules.DefaultModule
import izumi.distage.planning.solver.PlanVerifier
import izumi.distage.planning.solver.PlanVerifier.PlanVerifierResult
import izumi.distage.plugins.load.LoadedPlugins
import izumi.distage.roles.launcher.RoleProvider
import izumi.distage.roles.model.meta.{RoleBinding, RolesInfo}
import izumi.fundamentals.collections.nonempty.NESet
import izumi.fundamentals.platform.IzPlatform
import izumi.fundamentals.platform.cli.model.RoleAppArgs
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.language.Quirks
import izumi.logstage.api.IzLogger
import izumi.reflect.TagK

import scala.annotation.unused

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

  /**
    * Override this to execute additional arbitrary user-defined checks at compile-time (or runtime via `PlanCheck.runtime`)
    *
    * @throws Throwable You may throw a custom exception if your check error is not describable by [[izumi.distage.model.planning.PlanIssue]]
    */
  def customCheck(
    planVerifier: PlanVerifier,
    excludedActivations: Set[NESet[AxisPoint]],
    checkConfig: Boolean,
    planCheckInput: PlanCheckInput[AppEffectType],
  ): PlanVerifierResult = {
    Quirks.discard(planVerifier, excludedActivations, checkConfig, planCheckInput)
    PlanVerifierResult.empty
  }
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

abstract class RoleCheckableApp[F[_]](override implicit val tagK: TagK[F]) extends CheckableApp with RoleCheckableAppPlatformSpecific {
  def roleAppBootModule: Module

  override final type AppEffectType[A] = F[A]

  override def preparePlanCheckInput(
    selectedRoles: RoleSelection,
    chosenConfigFile: Option[String],
  ): PlanCheckInput[F] = {
    val maybeClassLoader = if (IzPlatform.isScalaJS) None else Option(this.getClass.getClassLoader)
    val baseModuleOverrides = roleAppBootModulePlanCheckOverrides(selectedRoles, chosenConfigFile.flatMap(configFile => maybeClassLoader.map(_ -> configFile)))
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
          providedKeys = injectorFactory.providedKeys[F](bsModule)(using DefaultModule[F](Module.make(defaultModuleBindings))),
          configLoader = configLoader,
          appPlugins = appPlugins,
          bsPlugins = bsPlugins,
        )
    })
  }

  protected final def roleAppBootModulePlanCheckOverrides(
    chosenRoles: RoleSelection,
    chosenConfigResource: Option[(ClassLoader, String)],
  ): ModuleDef = {
    new ModuleDef {
      make[IzLogger].named("early").fromValue(IzLogger.NullLogger)
      make[IzLogger].fromValue(IzLogger.NullLogger)

      make[AppConfig].fromValue(AppConfig.empty) // We set AppConfig to .empty to prevent implicit config loading?
      make[RoleAppArgs].fromValue(RoleAppArgs.empty)

      make[RoleProvider].from {
        chosenRoles match {
          case RoleSelection.Everything =>
            namePredicateRoleProvider(_ => true)

          case RoleSelection.AllExcluding(excluded) =>
            namePredicateRoleProvider(!excluded(_))

          case RoleSelection.OnlySelected(selection) =>
            selectedRoleProvider(selection)
        }
      }

      chosenConfigResource match {
        case Some((classLoader, resourceName)) =>
          make[ConfigLoader].fromValue(new SpecificResourceConfigLoader(classLoader, resourceName))
        case None =>
        // keep original ConfigLoader
      }

      private def namePredicateRoleProvider(predicate: String => Boolean): Functoid[RoleProvider] = {
        // use Auto-Traits feature to override just the few specific methods of a class without repeating its constructor
        @impl trait NamePredicateRoleProvider extends RoleProvider.NonReflectiveImpl {
          override protected def isRoleEnabled(@unused requiredRoles: Set[String])(b: RoleBinding): Boolean = {
            predicate(b.id)
          }
          override protected def getInfo(bindings: Set[Binding], @unused requiredRoles: Set[String], roleType: SafeType): RolesInfo = {
            super.getInfo(bindings, requiredRoles = Set.empty, roleType)
          }
        }

        TraitConstructor[NamePredicateRoleProvider]
      }

      private def selectedRoleProvider(selection: Set[String]): Functoid[RoleProvider] = {
        @impl trait SelectedRoleProvider extends RoleProvider.NonReflectiveImpl {
          override protected def getInfo(bindings: Set[Binding], @unused requiredRoles: Set[String], roleType: SafeType): RolesInfo = {
            super.getInfo(bindings, requiredRoles = selection, roleType)
          }
        }

        TraitConstructor[SelectedRoleProvider]
      }
    }
  }

  class SpecificResourceConfigLoader(val classLoader: ClassLoader, val resourceName: String) extends ConfigLoader {
    override def loadConfig(clue: String): AppConfig = {
      specificResourceConfigLoaderImpl(classLoader, resourceName, clue)
    }
  }

}
