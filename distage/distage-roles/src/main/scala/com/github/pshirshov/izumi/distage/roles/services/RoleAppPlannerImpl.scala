package com.github.pshirshov.izumi.distage.roles.services

import com.github.pshirshov.izumi.distage.model.GCMode
import com.github.pshirshov.izumi.distage.model.definition.{ModuleBase, ModuleDef}
import com.github.pshirshov.izumi.distage.model.monadic.{DIEffect, DIEffectRunner}
import com.github.pshirshov.izumi.distage.model.plan.{DependencyGraph, DependencyKind, ExecutableOp, PlanTopologyImmutable}
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
        make[RoleAppPlanner[F]].fromValue(RoleAppPlannerImpl.this)
        make[ContextOptions].fromValue(options)
        make[ModuleBase].named("application.module").fromValue(appModule)
      })

    val runtimeGcRoots: Set[DIKey] = Set(
      DIKey.get[DIEffectRunner[F]],
      DIKey.get[DIEffect[F]],
    )
    val runtimePlan = injector.plan(PlannerInput(fullAppModule, runtimeGcRoots))

    val rolesPlan = injector.plan(PlannerInput(fullAppModule, appMainRoots))

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

    AppStartupPlans(
      runtimePlan,
      integrationPlan,
      integrationComponents,
      refinedRolesPlan,
      injector
    )
  }


  private def emptyPlan(runtimePlan: OrderedPlan): OrderedPlan = {
    OrderedPlan(runtimePlan.definition, Vector.empty, GCMode.NoGC, PlanTopologyImmutable(DependencyGraph(Map.empty, DependencyKind.Depends), DependencyGraph(Map.empty, DependencyKind.Required)))
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
          val tree = s"\n${plan.renderDeps(deptree)}"
          logger.warn(s"Circular dependency has been resolved with proxy for ${s.target -> "key"}, $tree")
      }
    }
  }

}
