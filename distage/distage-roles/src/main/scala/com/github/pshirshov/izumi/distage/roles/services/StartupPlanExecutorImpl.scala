package com.github.pshirshov.izumi.distage.roles.services

import com.github.pshirshov.izumi.distage.model.Locator
import com.github.pshirshov.izumi.distage.model.monadic.{DIEffect, DIEffectRunner}
import com.github.pshirshov.izumi.distage.model.monadic.DIEffect.syntax._
import com.github.pshirshov.izumi.distage.roles.services.IntegrationChecker.IntegrationCheckException
import com.github.pshirshov.izumi.distage.roles.services.RoleAppPlanner.AppStartupPlans
import com.github.pshirshov.izumi.fundamentals.platform.functional.Identity
import distage.{Injector, TagK}

class StartupPlanExecutorImpl(
                               injector: Injector,
                               integrationChecker: IntegrationChecker
                             ) extends StartupPlanExecutor {
  final def execute[F[_]: TagK, A](appPlan: AppStartupPlans, filters: StartupPlanExecutor.Filters[F])(doRun: (Locator, Option[IntegrationCheckException], DIEffect[F]) => F[A]): A = {

    injector.produceFX[Identity](appPlan.runtime, filters.filterId)
      .use {
        runtimeLocator =>
          val runner = runtimeLocator.get[DIEffectRunner[F]]
          implicit val F: DIEffect[F] = runtimeLocator.get[DIEffect[F]]

          runner.run {
            Injector.inherit(runtimeLocator)
              .produceFX[F](appPlan.integration, filters.filterF)
              .use {
                integrationLocator =>
                  F.maybeSuspend {
                    integrationChecker.checkOrException(appPlan.integrationKeys, integrationLocator)
                  }.flatMap {
                    integrationCheckResult =>
                      Injector.inherit(integrationLocator)
                        .produceFX[F](appPlan.app, filters.filterF)
                        .use(doRun(_, integrationCheckResult, F))
                  }
              }
          }
      }
  }

}
