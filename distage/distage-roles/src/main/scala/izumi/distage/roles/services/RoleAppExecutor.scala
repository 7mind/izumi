package com.github.pshirshov.izumi.distage.roles.services

import com.github.pshirshov.izumi.distage.roles.services.RoleAppPlanner.AppStartupPlans


trait RoleAppExecutor[F[_]] {
  def runPlan(appPlan: AppStartupPlans): Unit
}

