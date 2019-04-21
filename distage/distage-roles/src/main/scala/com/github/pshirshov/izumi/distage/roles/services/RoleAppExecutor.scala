package com.github.pshirshov.izumi.distage.roles.services

import com.github.pshirshov.izumi.distage.plugins.MergedPlugins
import com.github.pshirshov.izumi.distage.roles.services.RoleAppExecutor.AppStartupPlans
import distage.{DIKey, Module, OrderedPlan}

trait RoleAppExecutor[F[_]] {
  def makePlan(defBs: MergedPlugins, defApp: MergedPlugins, appModule: Module): AppStartupPlans

  def runPlan(appPlan: AppStartupPlans): Unit
}

object RoleAppExecutor {
  case class AppStartupPlans(runtime: OrderedPlan, integration: OrderedPlan, integrationKeys: Set[DIKey], app: OrderedPlan)

}
