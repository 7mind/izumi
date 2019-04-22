package com.github.pshirshov.izumi.distage.roles.services

import com.github.pshirshov.izumi.distage.model.Locator
import com.github.pshirshov.izumi.distage.model.monadic.DIEffect
import com.github.pshirshov.izumi.distage.roles.DIEffectRunner
import com.github.pshirshov.izumi.distage.roles.services.RoleAppPlanner.AppStartupPlans
import distage.{Injector, TagK}

class StartupPlanExecutorImpl(
                               injector: Injector,
                               integrationChecker: IntegrationChecker,
                             ) extends StartupPlanExecutor {
  final def execute[F[_] : TagK](appPlan: AppStartupPlans)(doRun: (Locator, DIEffect[F]) => F[Unit]): Unit = {
    injector.produce(appPlan.runtime)
      .use {
        runtimeLocator =>
          val runner = runtimeLocator.get[DIEffectRunner[F]]
          implicit val effect: DIEffect[F] = runtimeLocator.get[DIEffect[F]]

          runner.run {
            Injector.inherit(runtimeLocator)
              .produceF[F](appPlan.integration)
              .use {
                integrationLocator =>
                  integrationChecker.checkOrFail(appPlan.integrationKeys, integrationLocator)


                  Injector.inherit(integrationLocator)
                    .produceF[F](appPlan.app)
                    .use(doRun(_, effect))
              }
          }
      }
  }

}
