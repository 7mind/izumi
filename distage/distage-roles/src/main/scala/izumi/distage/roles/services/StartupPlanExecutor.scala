package izumi.distage.roles.services

import distage.{Injector, TagK}
import izumi.distage.model.Locator
import izumi.distage.model.monadic.{DIEffect, DIEffectRunner}
import izumi.distage.model.provisioning.PlanInterpreter.FinalizersFilter
import izumi.distage.roles.services.RoleAppPlanner.AppStartupPlans
import izumi.fundamentals.platform.functional.Identity
import izumi.logstage.api.IzLogger
import DIEffect.syntax._

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
                .produceFX[F](appPlan.app.shared.plan, filters.filterF)
                .use {
                  sharedLocator =>


                    Injector.inherit(sharedLocator)
                      .produceFX[F](appPlan.app.side.plan, filters.filterF)
                      .use {
                        integrationLocator =>
                          effect.maybeSuspend(integrationChecker.checkOrFail(appPlan.app.side.roots, integrationLocator))
                      }
                      .flatMap {
                        _ =>
                          Injector.inherit(sharedLocator)
                            .produceFX[F](appPlan.app.primary.plan, filters.filterF)
                            .use(doRun(_, effect))
                      }
                }
            }
        }
    }

  }

}
