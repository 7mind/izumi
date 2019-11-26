package izumi.distage.roles.services

import distage.{Injector, TagK}
import izumi.distage.model.Locator
import izumi.distage.model.monadic.DIEffect.syntax._
import izumi.distage.model.monadic.{DIEffect, DIEffectRunner}
import izumi.distage.model.provisioning.PlanInterpreter.FinalizersFilter
import izumi.distage.roles.services.RoleAppPlanner.AppStartupPlans
import izumi.fundamentals.platform.functional.Identity
import izumi.logstage.api.IzLogger

trait StartupPlanExecutor[F[_]] {
  def execute(appPlan: AppStartupPlans, filters: StartupPlanExecutor.Filters[F])(doRun: (Locator, DIEffect[F]) => F[Unit]): Unit
}

object StartupPlanExecutor {
  def default[F[_]: TagK](logger: IzLogger, injector: Injector): StartupPlanExecutor[F] = {
    val checker = new IntegrationChecker.Impl[F](logger)
    new StartupPlanExecutor.Impl[F](injector, checker)
  }

  final case class Filters[F[_]](
    filterF: FinalizersFilter[F],
    filterId: FinalizersFilter[Identity],
  )

  object Filters {
    def all[F[_]]: Filters[F] = Filters[F](FinalizersFilter.all, FinalizersFilter.all)
  }

  class Impl[F[_]: TagK](
    injector: Injector,
    integrationChecker: IntegrationChecker[F],
  ) extends StartupPlanExecutor[F] {
    def execute(appPlan: AppStartupPlans, filters: StartupPlanExecutor.Filters[F])(doRun: (Locator, DIEffect[F]) => F[Unit]): Unit = {
      injector.produceFX[Identity](appPlan.runtime, filters.filterId).use {
        runtimeLocator =>
          val runner = runtimeLocator.get[DIEffectRunner[F]]
          implicit val effect: DIEffect[F] = runtimeLocator.get[DIEffect[F]]

          runner.run {
            Injector
              .inherit(runtimeLocator)
              .produceFX[F](appPlan.app.shared.plan, filters.filterF)
              .use {
                sharedLocator =>
                  Injector
                    .inherit(sharedLocator)
                    .produceFX[F](appPlan.app.side.plan, filters.filterF)
                    .use {
                      integrationLocator =>
                        integrationChecker.checkOrFail(appPlan.app.side.roots, integrationLocator)
                    }
                    .flatMap {
                      _ =>
                        Injector
                          .inherit(sharedLocator)
                          .produceFX[F](appPlan.app.primary.plan, filters.filterF)
                          .use(doRun(_, effect))
                    }
              }
          }
      }
    }

  }

}
