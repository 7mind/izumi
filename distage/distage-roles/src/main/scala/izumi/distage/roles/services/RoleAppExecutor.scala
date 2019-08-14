package izumi.distage.roles.services

import izumi.distage.roles.services.RoleAppPlanner.AppStartupPlans


trait RoleAppExecutor[F[_]] {
  def runPlan(appPlan: AppStartupPlans): Unit
}

