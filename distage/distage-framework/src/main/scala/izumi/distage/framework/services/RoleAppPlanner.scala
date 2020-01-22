package izumi.distage.framework.services

import distage.{BootstrapModule, DIKey, Injector, TagK, _}
import izumi.distage.framework.config.PlanningOptions
import izumi.distage.framework.model.IntegrationCheck
import izumi.distage.framework.services.RoleAppPlanner.AppStartupPlans
import izumi.distage.model.definition.{ModuleBase, ModuleDef}
import izumi.distage.model.effect.{DIEffect, DIEffectAsync, DIEffectRunner}
import izumi.distage.model.plan.{OrderedPlan, TriSplittedPlan}
import izumi.logstage.api.IzLogger

trait RoleAppPlanner[F[_]] {
  def reboot(bsModule: BootstrapModule): RoleAppPlanner[F]
  def makePlan(appMainRoots: Set[DIKey], appModule: ModuleBase): AppStartupPlans
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
                        ) extends RoleAppPlanner[F] { self =>
    private[this] val injector = Injector(bsModule)

    override def reboot(bsOverride: BootstrapModule): RoleAppPlanner[F] = {
      new RoleAppPlanner.Impl[F](options, bsModule overridenBy bsOverride, logger)
    }

    override def makePlan(appMainRoots: Set[DIKey], appModule: ModuleBase): AppStartupPlans = {
      val fullAppModule = appModule
        .overridenBy(new ModuleDef {
          make[RoleAppPlanner[F]].from(self)
          make[PlanningOptions].fromValue(options)
          make[ModuleBase].named("application.module").from(appModule)
        })

      val runtimeGcRoots: Set[DIKey] = Set(
        DIKey.get[DIEffectRunner[F]],
        DIKey.get[DIEffect[F]],
        DIKey.get[DIEffectAsync[F]],
      )

      val runtimePlan = injector.plan(PlannerInput(fullAppModule, runtimeGcRoots))

      val appPlan = injector.trisectByKeys(fullAppModule.drop(runtimeGcRoots), appMainRoots) {
        _.collectChildren[IntegrationCheck].map(_.target).toSet
      }

      val check = new PlanCircularDependencyCheck(options, logger)
      check.verify(runtimePlan)
      check.verify(appPlan.shared)
      check.verify(appPlan.side)
      check.verify(appPlan.primary)

      AppStartupPlans(runtimePlan, appPlan, injector)
    }
  }

}
