package com.github.pshirshov.izumi.distage.roles.services

import com.github.pshirshov.izumi.distage.model.definition.ModuleBase
import com.github.pshirshov.izumi.distage.model.plan.OrderedPlan
import com.github.pshirshov.izumi.distage.roles.services.RoleAppPlanner.AppStartupPlans
import distage.{BootstrapModule, DIKey, Injector}

trait RoleAppPlanner[F[_]] {
  def reboot(bsModule: BootstrapModule): RoleAppPlannerImpl[F]
  def makePlan(appMainRoots: Set[DIKey], appModule: ModuleBase): AppStartupPlans
}

object RoleAppPlanner {
  case class AppStartupPlans(
                              runtime: OrderedPlan,
                              integration: OrderedPlan,
                              integrationKeys: Set[DIKey],
                              app: OrderedPlan,
                              injector: Injector
                            )
}
