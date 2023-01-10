package izumi.distage.framework.services

import distage.{Injector, PlannerInput}
import izumi.distage.framework.config.PlanningOptions
import izumi.distage.framework.services.RoleAppPlanner.AppStartupPlans
import izumi.distage.model.definition.{Activation, BootstrapModule, Id}
import izumi.distage.model.effect.{QuasiAsync, QuasiIO, QuasiIORunner}
import izumi.distage.model.plan.{Plan, Roots}
import izumi.distage.model.recursive.{BootConfig, Bootloader, BootstrappedApp}
import izumi.distage.model.reflection.DIKey
import izumi.distage.modules.DefaultModule
import izumi.distage.planning.DIFailureInterpreter
import izumi.fundamentals.platform.functional.Identity
import izumi.logstage.api.IzLogger
import izumi.reflect.TagK

trait RoleAppPlanner {
  def reboot(bsModule: BootstrapModule): RoleAppPlanner
  def makePlan(appMainRoots: Set[DIKey]): AppStartupPlans
}

object RoleAppPlanner {

  final case class AppStartupPlans(
    runtime: Plan,
    app: Plan,
    injector: Injector[Identity],
  )

  class Impl[F[_]: TagK](
    options: PlanningOptions,
    activation: Activation @Id("roleapp"),
    bsModule: BootstrapModule @Id("roleapp"),
    bootloader: Bootloader @Id("roleapp"),
    logger: IzLogger,
  )(implicit
    defaultModule: DefaultModule[F]
  ) extends RoleAppPlanner { self =>

    private[this] val runtimeGcRoots: Set[DIKey] = Set(
      DIKey.get[QuasiIORunner[F]],
      DIKey.get[QuasiIO[F]],
      DIKey.get[QuasiAsync[F]],
    )

    override def reboot(bsOverride: BootstrapModule): RoleAppPlanner = {
      new RoleAppPlanner.Impl[F](options, activation, bsModule overriddenBy bsOverride, bootloader, logger)
    }

    override def makePlan(appMainRoots: Set[DIKey]): AppStartupPlans = {
      val interpreter = new DIFailureInterpreter(activation)
      import interpreter.DIResultExt

      logger.trace(s"Application will use: ${appMainRoots -> "app roots"} and $activation")

      // TODO: why .module doesn't work within for-comprehension?..
      def log(runtimeBsApp: BootstrappedApp): Either[Nothing, Unit] = Right {
        logger.trace(s"Bootstrap plan:\n${runtimeBsApp.plan.render() -> "bootstrap dump" -> null}")
        logger.trace(s"App module: ${runtimeBsApp.module -> "app module" -> null}")
      }

      val out = for {
        bootstrapped <- bootloader.boot(
          BootConfig(
            bootstrap = _ => bsModule,
            activation = _ => activation,
            roots = _ => Roots(runtimeGcRoots),
          )
        )
        runtimeKeys = bootstrapped.plan.keys
        _ <- log(bootstrapped)
        appPlan <- bootstrapped.injector.plan(PlannerInput(bootstrapped.module.drop(runtimeKeys), activation, appMainRoots))
      } yield {
        val check = new PlanCircularDependencyCheck(options, logger)

        check.showProxyWarnings(bootstrapped.plan)
        check.showProxyWarnings(appPlan)

        logger.info(s"Planning finished. ${appPlan.keys.size -> "main ops"} ${bootstrapped.plan.keys.size -> "runtime ops"}")
        logger.debug(s"Plan:\n${appPlan.render() -> "plan dump" -> null}")

        AppStartupPlans(bootstrapped.plan, appPlan, bootstrapped.injector)
      }

      out.getOrThrow
    }

  }

}
