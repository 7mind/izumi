package izumi.distage.roles.services

import distage.{Injector, TagK}
import izumi.distage.framework.services.IntegrationChecker
import izumi.distage.framework.services.RoleAppPlanner.AppStartupPlans
import izumi.distage.model.Locator
import izumi.distage.model.effect.DIEffect.syntax._
import izumi.distage.model.effect.{DIEffect, DIEffectRunner}
import izumi.distage.model.provisioning.PlanInterpreter.FinalizerFilter
import izumi.fundamentals.platform.functional.Identity

trait StartupPlanExecutor[F[_]] {
  def execute(appPlan: AppStartupPlans, filters: StartupPlanExecutor.Filters[F])(doRun: (Locator, DIEffect[F]) => F[Unit]): Unit
}

object StartupPlanExecutor {
  def apply[F[_]: TagK](injector: Injector, checker: IntegrationChecker[F]): StartupPlanExecutor[F] = {
    new StartupPlanExecutor.Impl[F](injector, checker)
  }

  final case class Filters[F[_]](
    filterF: FinalizerFilter[F],
    filterId: FinalizerFilter[Identity],
  )
  object Filters {
    def all[F[_]]: Filters[F] = Filters[F](FinalizerFilter.all, FinalizerFilter.all)
  }

  class Impl[F[_]: TagK](
    injector: Injector,
    integrationChecker: IntegrationChecker[F],
  ) extends StartupPlanExecutor[F] {
    def execute(appPlan: AppStartupPlans, filters: StartupPlanExecutor.Filters[F])(doRun: (Locator, DIEffect[F]) => F[Unit]): Unit = {
      injector
        .produceFX[Identity](appPlan.runtime, filters.filterId)
        .use {
          runtimeLocator =>
            val runner = runtimeLocator.get[DIEffectRunner[F]]
            implicit val effect: DIEffect[F] = runtimeLocator.get[DIEffect[F]]

            runner.run {
              Injector
                .inherit(runtimeLocator)
                .produceFX[F](appPlan.app.shared, filters.filterF)
                .use {
                  sharedLocator =>
                    Injector
                      .inherit(sharedLocator)
                      .produceFX[F](appPlan.app.side, filters.filterF)
                      .use {
                        integrationLocator =>
                          integrationChecker.checkOrFail(appPlan.app.side.declaredRoots, integrationLocator)
                      }
                      .flatMap {
                        _ =>
                          Injector
                            .inherit(sharedLocator)
                            .produceFX[F](appPlan.app.primary, filters.filterF)
                            .use(doRun(_, effect))
                      }
                }
            }
        }
    }

  }

}
