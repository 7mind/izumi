package com.github.pshirshov.izumi.distage.roles.services

import com.github.pshirshov.izumi.distage.model.definition.{ModuleBase, ModuleDef}
import com.github.pshirshov.izumi.distage.model.monadic.{DIEffect, DIEffectRunner}
import com.github.pshirshov.izumi.distage.roles.model.{AppActivation, IntegrationCheck}
import com.github.pshirshov.izumi.distage.roles.services.ModuleProviderImpl.ContextOptions
import com.github.pshirshov.izumi.distage.roles.services.RoleAppPlanner.AppStartupPlans
import com.github.pshirshov.izumi.logstage.api.IzLogger
import distage._

class RoleAppPlannerImpl[F[_] : TagK](
                                       options: ContextOptions,
                                       bsModule: BootstrapModule,
                                       activation: AppActivation,
                                       logger: IzLogger,
                                     ) extends RoleAppPlanner[F] {

  private val injector = Injector.Standard(bsModule)

  def reboot(bsOverride: BootstrapModule): RoleAppPlannerImpl[F] = {
    new RoleAppPlannerImpl[F](options, bsModule overridenBy bsOverride, activation, logger)
  }

  def makePlan(appMainRoots: Set[DIKey], appModule: ModuleBase): AppStartupPlans = {
    val fullAppModule = appModule
      .overridenBy(new ModuleDef {
        make[RoleAppPlanner[F]].from(RoleAppPlannerImpl.this)
        make[ContextOptions].from(options)
        make[ModuleBase].named("application.module").from(appModule)
      })

    val runtimeGcRoots: Set[DIKey] = Set(
      DIKey.get[DIEffectRunner[F]],
      DIKey.get[DIEffect[F]],
    )
    val runtimePlan = injector.plan(PlannerInput(fullAppModule, runtimeGcRoots))

    val appPlan = injector.splitPlan(fullAppModule.drop(runtimeGcRoots), appMainRoots) {
      _.collectChildren[IntegrationCheck].map(_.target).toSet
    }

    val check = new PlanCircularDependencyCheck(options, logger)
    check.verify(runtimePlan)
    check.verify(appPlan.subplan)
    check.verify(appPlan.primary)

    AppStartupPlans(
      runtimePlan,
      appPlan.subplan,
      appPlan.subRoots,
      appPlan.primary,
      injector
    )
  }
}

