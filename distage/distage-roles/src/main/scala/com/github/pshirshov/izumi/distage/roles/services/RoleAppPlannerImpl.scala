package com.github.pshirshov.izumi.distage.roles.services

import com.github.pshirshov.izumi.distage.model.definition.ModuleDef
import com.github.pshirshov.izumi.distage.model.monadic.DIEffect
import com.github.pshirshov.izumi.distage.model.plan.{DependencyGraph, DependencyKind, PlanTopologyImmutable}
import com.github.pshirshov.izumi.distage.roles.services.RoleAppPlanner.AppStartupPlans
import com.github.pshirshov.izumi.distage.roles.{DIEffectRunner, IntegrationCheck, RolesInfo}
import distage._

class RoleAppPlannerImpl[F[_] : TagK : DIEffect](
                                                  _appModule: distage.Module,
                                                   roles: RolesInfo,
                                                   injector: Injector,
                                                 ) extends RoleAppPlanner[F] {
  def makePlan(appMainRoots: Set[DIKey]): AppStartupPlans = {
    val runtimeGcRoots: Set[DIKey] = Set(
      DIKey.get[DIEffectRunner[F]],
      DIKey.get[Finalizers.ExecutorsFinalized],
    )

    val appModule = _appModule.overridenBy(new ModuleDef {
      make[RoleAppPlanner[F]].from(RoleAppPlannerImpl.this)
    })

    val runtimePlan = injector.plan(appModule, runtimeGcRoots)

    val rolesPlan = injector.plan(appModule, appMainRoots)

    val integrationComponents = rolesPlan.collectChildren[IntegrationCheck].map(_.target).toSet

    val integrationPlan = if (integrationComponents.nonEmpty) {
      // exclude runtime
      injector.plan(PlannerInput(appModule.drop(runtimePlan.keys), integrationComponents))
    } else {
      emptyPlan(runtimePlan)
    }

    val refinedRolesPlan = if (appMainRoots.nonEmpty) {
      // exclude runtime & integrations
      injector.plan(PlannerInput(appModule.drop(integrationPlan.keys), appMainRoots))
    } else {
      emptyPlan(runtimePlan)
    }
    //    println("====")
    //    println(runtimePlan.render())
    //    println("----")
    //    println("====")
    //    println(integrationPlan.render())
    //    println("----")
    //    println("====")
    //    println(refinedRolesPlan.render())
    //    println("----")

    AppStartupPlans(runtimePlan, integrationPlan, integrationComponents, refinedRolesPlan)
  }




  private def emptyPlan(runtimePlan: OrderedPlan): OrderedPlan = {
    OrderedPlan(runtimePlan.definition, Vector.empty, Set.empty, PlanTopologyImmutable(DependencyGraph(Map.empty, DependencyKind.Depends), DependencyGraph(Map.empty, DependencyKind.Required)))
  }

}
