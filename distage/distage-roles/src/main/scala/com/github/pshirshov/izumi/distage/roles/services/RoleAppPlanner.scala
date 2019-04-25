package com.github.pshirshov.izumi.distage.roles.services

import com.github.pshirshov.izumi.distage.model.plan.OrderedPlan
import com.github.pshirshov.izumi.distage.roles.services.RoleAppPlanner.AppStartupPlans
import distage.DIKey

trait RoleAppPlanner[F[_]] {
  def makePlan(appMainRoots: Set[DIKey]): AppStartupPlans

}

object RoleAppPlanner {
  case class AppStartupPlans(runtime: OrderedPlan, integration: OrderedPlan, integrationKeys: Set[DIKey], app: OrderedPlan)
}
