package com.github.pshirshov.izumi.distage.roles.services

import com.github.pshirshov.izumi.distage.model.definition.ModuleDef
import com.github.pshirshov.izumi.distage.model.monadic.{DIEffect, DIEffectRunner}
import com.github.pshirshov.izumi.distage.model.plan.{DependencyGraph, DependencyKind, ExecutableOp, PlanTopologyImmutable}
import com.github.pshirshov.izumi.distage.roles.model.IntegrationCheck
import com.github.pshirshov.izumi.distage.roles.services.RoleAppPlanner.AppStartupPlans
import com.github.pshirshov.izumi.distage.roles.services.ModuleProviderImpl.ContextOptions
import com.github.pshirshov.izumi.logstage.api.IzLogger
import distage._

class RoleAppPlannerImpl[F[_] : TagK](
                                       options: ContextOptions,
                                       appModule: distage.ModuleBase,
                                       injector: Injector,
                                       logger: IzLogger,
                                     ) extends RoleAppPlanner[F] {
  def makePlan(appMainRoots: Set[DIKey]): AppStartupPlans = {
    val runtimeGcRoots: Set[DIKey] = Set(
      DIKey.get[DIEffectRunner[F]],
      DIKey.get[DIEffect[F]],
    )

    val fullAppModule = appModule.overridenBy(new ModuleDef {
      make[RoleAppPlanner[F]].from(RoleAppPlannerImpl.this)
    })

    val runtimePlan = injector.plan(fullAppModule, runtimeGcRoots)

    val rolesPlan = injector.plan(fullAppModule, appMainRoots)

    val integrationComponents = rolesPlan.collectChildren[IntegrationCheck].map(_.target).toSet

    val integrationPlan = if (integrationComponents.nonEmpty) {
      // exclude runtime
      injector.plan(PlannerInput(fullAppModule.drop(runtimePlan.keys), integrationComponents))
    } else {
      emptyPlan(runtimePlan)
    }

    val refinedRolesPlan = if (appMainRoots.nonEmpty) {
      // exclude runtime & integrations
      injector.plan(PlannerInput(fullAppModule.drop(integrationPlan.keys), appMainRoots))
    } else {
      emptyPlan(runtimePlan)
    }
//            println("====")
//            println(runtimePlan.render())
//            println("----")
//            println("====")
//            println(integrationPlan.render())
//            println("----")
//            println("====")
//            println(refinedRolesPlan.render())
//            println("----")

    verify(runtimePlan)
    verify(integrationPlan)
    verify(refinedRolesPlan)

    AppStartupPlans(runtimePlan, integrationPlan, integrationComponents, refinedRolesPlan)
  }


  private def emptyPlan(runtimePlan: OrderedPlan): OrderedPlan = {
    OrderedPlan(runtimePlan.definition, Vector.empty, Set.empty, PlanTopologyImmutable(DependencyGraph(Map.empty, DependencyKind.Depends), DependencyGraph(Map.empty, DependencyKind.Required)))
  }

  private def verify(plan: OrderedPlan): Unit = {
    if (options.warnOnCircularDeps) {
      val allProxies = plan.steps.collect {
        case s: ExecutableOp.ProxyOp.MakeProxy =>
          s
      }

      allProxies.foreach {
        s =>
          val deptree = plan.topology.dependencies.tree(s.target)
          val tree = s"\n${deptree.render()}"
          logger.warn(s"Circular dependency has been resolved with proxy for ${s.target -> "key"}, $tree")
      }
    }
  }

}
