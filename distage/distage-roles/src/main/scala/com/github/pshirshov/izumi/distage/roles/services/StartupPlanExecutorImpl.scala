package com.github.pshirshov.izumi.distage.roles.services

import com.github.pshirshov.izumi.distage.model.Locator
import com.github.pshirshov.izumi.distage.model.monadic.DIEffect
import com.github.pshirshov.izumi.distage.model.provisioning.PlanInterpreter.FinalizersFilter
import com.github.pshirshov.izumi.distage.roles.DIEffectRunner
import com.github.pshirshov.izumi.distage.roles.services.RoleAppPlanner.AppStartupPlans
import com.github.pshirshov.izumi.fundamentals.platform.functional.Identity
import distage.{Injector, TagK}

class StartupPlanExecutorImpl(
                               injector: Injector,
                               integrationChecker: IntegrationChecker
                             ) extends StartupPlanExecutor {
  final def execute[F[_] : TagK](appPlan: AppStartupPlans, filters: StartupPlanExecutor.Filters[F])(doRun: (Locator, DIEffect[F]) => F[Unit]): Unit = {

    injector.produceFX[Identity](appPlan.runtime, filters.filterId)
      .use {
        runtimeLocator =>
          val runner = runtimeLocator.get[DIEffectRunner[F]]
          implicit val effect: DIEffect[F] = runtimeLocator.get[DIEffect[F]]

          runner.run {
            Injector.inherit(runtimeLocator)
              .produceFX[F](appPlan.integration, filters.filterF)
              .use {
                integrationLocator =>
                  integrationChecker.checkOrFail(appPlan.integrationKeys, integrationLocator)


                  Injector.inherit(integrationLocator)
                    .produceFX[F](appPlan.app, filters.filterF)
                    .use(doRun(_, effect))
              }
          }
      }
  }

}
