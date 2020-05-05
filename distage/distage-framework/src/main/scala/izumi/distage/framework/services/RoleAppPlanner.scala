package izumi.distage.framework.services

import distage.{BootstrapModule, DIKey, Injector, TagK, _}
import izumi.distage.framework.config.PlanningOptions
import izumi.distage.framework.model.IntegrationCheck
import izumi.distage.framework.services.RoleAppPlanner.AppStartupPlans
import izumi.distage.model.definition.ModuleDef
import izumi.distage.model.effect.{DIEffect, DIEffectAsync, DIEffectRunner}
import izumi.distage.model.plan.{OrderedPlan, TriSplittedPlan}
import izumi.distage.model.recursive.{BootConfig, Bootloader}
import izumi.logstage.api.IzLogger

trait RoleAppPlanner[F[_]] {
  def reboot(bsModule: BootstrapModule): RoleAppPlanner[F]
  def makePlan(appMainRoots: Set[DIKey] /*, appModule: ModuleBase*/ ): AppStartupPlans
}

object RoleAppPlanner {

  final case class AppStartupPlans(
    runtime: OrderedPlan,
    app: TriSplittedPlan,
    injector: Injector,
  )

  class Impl[F[_]: TagK](
    options: PlanningOptions,
    bsModule: BootstrapModule,
    logger: IzLogger,
    bootloader: Bootloader,
  ) extends RoleAppPlanner[F] {
    self =>

    private val runtimeGcRoots: Set[DIKey] = Set(
      DIKey.get[DIEffectRunner[F]],
      DIKey.get[DIEffect[F]],
      DIKey.get[DIEffectAsync[F]],
    )

    override def reboot(bsOverride: BootstrapModule): RoleAppPlanner[F] = {
      new RoleAppPlanner.Impl[F](options, bsModule overridenBy bsOverride, logger, bootloader)
    }

    override def makePlan(appMainRoots: Set[DIKey]): AppStartupPlans = {
      val additionalModule = selfReflectionModule()
      val app = bootloader.boot(
        BootConfig(
          bootstrap = _ => bsModule,
          appModule = _.overridenBy(additionalModule),
          gcMode = _ => GCMode(runtimeGcRoots),
        )
      )

      val appPlan = app.injector.trisectByKeys(bootloader.activation, app.module.drop(runtimeGcRoots), appMainRoots) {
        _.collectChildren[IntegrationCheck].map(_.target).toSet
      }

      val check = new PlanCircularDependencyCheck(options, logger)
      check.verify(app.plan)
      check.verify(appPlan.shared)
      check.verify(appPlan.side)
      check.verify(appPlan.primary)

      AppStartupPlans(app.plan, appPlan, app.injector)
    }

    private def selfReflectionModule(): ModuleDef = new ModuleDef {
      make[RoleAppPlanner[F]].fromValue(self)
      make[PlanningOptions].fromValue(options)
    }
  }

}
