package izumi.distage.framework

import distage.{Injector, impl}
import izumi.distage.model.definition.{BootstrapModule, Id, ModuleDef}
import izumi.distage.model.recursive.{BootConfig, Bootloader}
import izumi.distage.roles.RoleAppMain
import izumi.distage.roles.RoleAppMain.ArgV
import izumi.distage.roles.launcher.RoleProvider
import izumi.distage.roles.model.meta.RoleBinding
import izumi.fundamentals.platform.functional.Identity

object PlanCheck {

  def checkRoleApp[F[_]](roleAppMain: RoleAppMain[F]): Unit = {
    val roleAppBootstrapModule = roleAppMain.finalAppModule(ArgV(Array.empty))
    Injector[Identity]().produceRun(roleAppBootstrapModule overriddenBy new ModuleDef {

      @impl trait ConfiguredRoleProvider extends RoleProvider.Impl {
        override protected def isRoleEnabled(requiredRoles: Set[String])(b: RoleBinding): Boolean = true
      }

      make[RoleProvider].from[ConfiguredRoleProvider]

    }) {
      (bootloader: Bootloader @Id("roleapp"), bsModule: BootstrapModule @Id("roleapp")) =>
        val app = bootloader.boot(
          BootConfig(
            bootstrap = _ => bsModule
          )
        )
        println(app.plan.render())
    }
  }

}
