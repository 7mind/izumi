package izumi.distage.roles.services

import izumi.distage.model.monadic.DIEffect
import izumi.distage.model.provisioning.PlanInterpreter.FinalizersFilter
import izumi.distage.roles.services.RoleAppPlanner.AppStartupPlans
import izumi.fundamentals.platform.functional.Identity
import izumi.logstage.api.IzLogger
import distage.{Injector, Locator, TagK}

trait StartupPlanExecutor {
  def execute[F[_] : TagK](appPlan: AppStartupPlans, filters: StartupPlanExecutor.Filters[F])(doRun: (Locator, DIEffect[F]) => F[Unit]): Unit
}

object StartupPlanExecutor {
  def default(logger: IzLogger, injector: Injector): StartupPlanExecutor = {
    val checker = new IntegrationCheckerImpl(logger)
    new StartupPlanExecutorImpl(injector, checker)
  }

case class Filters[F[_]](
                            filterF: FinalizersFilter[F],
                            filterId: FinalizersFilter[Identity],
                          )

  object Filters {
    def all[F[_]]: Filters[F] = Filters[F](FinalizersFilter.all, FinalizersFilter.all)
  }
}
