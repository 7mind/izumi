package izumi.distage.framework

import distage.{Injector, Mode, impl}
import izumi.distage.config.model.AppConfig
import izumi.distage.model.definition._
import izumi.distage.model.plan.ExecutableOp.ImportDependency
import izumi.distage.model.recursive.{BootConfig, Bootloader, LocatorRef}
import izumi.distage.roles.RoleAppMain
import izumi.distage.roles.RoleAppMain.ArgV
import izumi.distage.roles.launcher.RoleProvider
import izumi.distage.roles.model.meta.RoleBinding
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.strings.IzString.toRichIterable

object PlanCheck {

  def checkRoleApp[F[_]](roleAppMain: RoleAppMain[F]): Unit = {
    import roleAppMain.tagK

    val roleAppBootstrapModule = roleAppMain.finalAppModule(ArgV(Array.empty))
    Injector[Identity]().produceRun(roleAppBootstrapModule overriddenBy new ModuleDef {

      @impl trait ConfiguredRoleProvider extends RoleProvider.Impl {
        override protected def isRoleEnabled(requiredRoles: Set[String])(b: RoleBinding): Boolean = true
      }

      make[RoleProvider].from[ConfiguredRoleProvider]
      make[AppConfig].fromValue(AppConfig.empty)
      private val axis1: Axis = new Axis {
        override def name: String = "axiscomponentaxis"
      }
      make[Activation].named("primary").fromValue {
        Activation(
          Mode -> Mode.Prod,
          axis1 -> new axis1.AxisValueDef {
            override def id: String = "correct"
          },
        )
      }
    }) {
      (bootloader: Bootloader @Id("roleapp"), bsModule: BootstrapModule @Id("roleapp"), locatorRef: LocatorRef) =>
        val app = bootloader.boot(
          BootConfig(
            bootstrap = _ => bsModule
          )
        )
        val keysUsedInBootstrap = locatorRef.get.allInstances.iterator.map(_.key).toSet
        val futureKeys = roleAppBootstrapModule.keys
        val allKeys = keysUsedInBootstrap ++ futureKeys

        println(allKeys.map(_.toString).niceList())
        println(app.plan.render())

        app
          .plan
          .resolveImports {
            case ImportDependency(target, _, _) if allKeys(target) || target.tpe.tag.shortName.contains("XXX_LocatorLeak") => null
          }
          .assertValidOrThrow[F]()
    }
  }

}
