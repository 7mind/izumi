package izumi.distage.framework.services

import distage.{BootstrapModule, DIKey, Injector, TagK, _}
import izumi.distage.framework.config.PlanningOptions
import izumi.distage.framework.model.IntegrationCheck
import izumi.distage.framework.services.RoleAppPlanner.AppStartupPlans
import izumi.distage.model.definition.ModuleDef
import izumi.distage.model.effect.{DIEffect, DIEffectAsync, DIEffectRunner}
import izumi.distage.model.plan.{OrderedPlan, TriSplittedPlan}
import izumi.distage.model.recursive.{BootConfig, Bootloader}
import izumi.fundamentals.platform.functional.Identity
import izumi.logstage.api.IzLogger

trait RoleAppPlanner[F[_]] {
  def reboot(bsModule: BootstrapModule): RoleAppPlanner[F]
  def makePlan(appMainRoots: Set[DIKey] /*, appModule: ModuleBase*/ ): AppStartupPlans
}

object RoleAppPlanner {

  final case class AppStartupPlans(
    runtime: OrderedPlan,
    app: TriSplittedPlan,
    injector: Injector[Identity],
  )

  class Impl[F[_]: TagK](
    options: PlanningOptions,
    bsModule: BootstrapModule @Id("roleapp"),
    bootloader: Bootloader @Id("roleapp"),
    logger: IzLogger,
  )(implicit
    defaultModule: DefaultModule[F]
  ) extends RoleAppPlanner[F] { self =>

    private[this] val runtimeGcRoots: Set[DIKey] = Set(
      DIKey.get[DIEffectRunner[F]],
      DIKey.get[DIEffect[F]],
      DIKey.get[DIEffectAsync[F]],
    )

    override def reboot(bsOverride: BootstrapModule): RoleAppPlanner[F] = {
      new RoleAppPlanner.Impl[F](options, bsModule overridenBy bsOverride, bootloader, logger)
    }

    override def makePlan(appMainRoots: Set[DIKey]): AppStartupPlans = {
      val selfReflectionModule = new ModuleDef {
        make[RoleAppPlanner[F]].fromValue(self)
        make[PlanningOptions].fromValue(options)
      }
      val bootstrappedApp = bootloader.boot(
        BootConfig(
          bootstrap = _ => bsModule,
          appModule = _ overridenBy selfReflectionModule,
          roots = _ => Roots(runtimeGcRoots),
        )
      )
      val runtimeKeys = bootstrappedApp.plan.keys

      val appPlan = bootstrappedApp.injector.trisectByKeys(bootloader.activation, bootstrappedApp.module.drop(runtimeKeys), appMainRoots) {
        _.collectChildrenKeysSplit[IntegrationCheck[Identity], IntegrationCheck[F]]
      }

      val check = new PlanCircularDependencyCheck(options, logger)
      check.verify(bootstrappedApp.plan)
      check.verify(appPlan.shared)
      check.verify(appPlan.side)
      check.verify(appPlan.primary)

      val out = AppStartupPlans(bootstrappedApp.plan, appPlan, bootstrappedApp.injector)
      logger.info(
        s"Planning finished. ${out.app.primary.keys.size -> "main ops"}, ${out.app.side.keys.size -> "integration ops"}, ${out
          .app.shared.keys.size -> "shared ops"}, ${out.runtime.keys.size -> "runtime ops"}"
      )
      out
    }

  }

}
