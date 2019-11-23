package izumi.distage.roles.services

import distage.{Injector, TagK}
import izumi.distage.model.Locator
import izumi.distage.model.monadic.{DIEffect, DIEffectRunner}
import izumi.distage.model.provisioning.PlanInterpreter.FinalizersFilter
import izumi.distage.roles.services.RoleAppPlanner.AppStartupPlans
import izumi.fundamentals.platform.functional.Identity
import izumi.logstage.api.IzLogger

trait StartupPlanExecutor {
  def execute[F[_]: TagK](appPlan: AppStartupPlans, filters: StartupPlanExecutor.Filters[F])(doRun: (Locator, DIEffect[F]) => F[Unit]): Unit
}

object StartupPlanExecutor {
  def default(logger: IzLogger, injector: Injector): StartupPlanExecutor = {
    val checker = new IntegrationChecker.Impl(logger)
    new StartupPlanExecutor.Impl(injector, checker)
  }

  final case class Filters[F[_]](
                                  filterF: FinalizersFilter[F],
                                  filterId: FinalizersFilter[Identity],
                                )
  object Filters {
    def all[F[_]]: Filters[F] = Filters[F](FinalizersFilter.all, FinalizersFilter.all)
  }

  class Impl(
              injector: Injector,
              integrationChecker: IntegrationChecker,
            ) extends StartupPlanExecutor {
    def execute[F[_]: TagK](appPlan: AppStartupPlans, filters: StartupPlanExecutor.Filters[F])(doRun: (Locator, DIEffect[F]) => F[Unit]): Unit = {
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

}
