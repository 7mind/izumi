package com.github.pshirshov.izumi.distage.roles.services

import com.github.pshirshov.izumi.distage.model.monadic.DIEffect
import com.github.pshirshov.izumi.distage.roles.services.RoleAppPlanner.AppStartupPlans
import com.github.pshirshov.izumi.logstage.api.IzLogger
import distage.{Injector, Locator, TagK}

object StartupPlanExecutor {
  def default(logger: IzLogger, injector: Injector): StartupPlanExecutor = {
    val checker = new IntegrationCheckerImpl(logger)
    new StartupPlanExecutorImpl(injector, checker)
  }
}

trait StartupPlanExecutor {
  def execute[F[_] : TagK](appPlan: AppStartupPlans)(doRun: (Locator, DIEffect[F]) => F[Unit]): Unit
}
