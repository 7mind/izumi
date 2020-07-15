package izumi.distage.roles.services

import distage.{Injector, TagK}
import izumi.distage.framework.services.IntegrationChecker
import izumi.distage.framework.services.RoleAppPlanner.AppStartupPlans
import izumi.distage.model.Locator
import izumi.distage.model.definition.DIResource
import izumi.distage.model.effect.{DIEffect, DIEffectRunner}
import izumi.distage.model.provisioning.PlanInterpreter.FinalizerFilter
import izumi.distage.roles.services.StartupPlanExecutor.PreparedApp
import izumi.fundamentals.platform.functional.Identity

trait StartupPlanExecutor[F[_]] {
  def execute(
    appPlan: AppStartupPlans,
    filters: StartupPlanExecutor.Filters[F],
  )(doRun: (Locator, DIEffect[F]) => F[Unit]
  ): DIResource.DIResourceBase[Identity, PreparedApp[F]]
}

object StartupPlanExecutor {
  case class PreparedApp[F[_]](
    app: DIResource.DIResourceBase[F, Locator],
    runner: DIEffectRunner[F],
    effect: DIEffect[F],
  ) {
    def run(): Unit = {
      runner.run(app.use(_ => effect.unit)(effect))
    }
  }

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
    def execute(
      appPlan: AppStartupPlans,
      filters: StartupPlanExecutor.Filters[F],
    )(doRun: (Locator, DIEffect[F]) => F[Unit]
    ): DIResource.DIResourceBase[Identity, PreparedApp[F]] = {
      injector
        .produceFX[Identity](appPlan.runtime, filters.filterId)
        .map {
          runtimeLocator =>
            val runner = runtimeLocator.get[DIEffectRunner[F]]
            implicit val effect: DIEffect[F] = runtimeLocator.get[DIEffect[F]]

            PreparedApp(prepareMainResource(appPlan, filters, doRun, runtimeLocator)(effect), runner, effect)
        }
    }

    private def prepareMainResource(
      appPlan: AppStartupPlans,
      filters: Filters[F],
      doRun: (Locator, DIEffect[F]) => F[Unit],
      runtimeLocator: Locator,
    )(implicit
      effect: DIEffect[F]
    ): DIResource.DIResourceBase[F, Locator] = {
      Injector
        .inherit(runtimeLocator)
        .produceFX[F](appPlan.app.shared, filters.filterF)
        .flatMap {
          sharedLocator =>
            Injector
              .inherit(sharedLocator)
              .produceFX[F](appPlan.app.side, filters.filterF)
              .evalTap {
                integrationLocator =>
                  integrationChecker.checkOrFail(appPlan.app.side.declaredRoots, integrationLocator)
              }
              .flatMap {
                _ => // we don't need integration locator
                  Injector
                    .inherit(sharedLocator)
                    .produceFX[F](appPlan.app.primary, filters.filterF)
                    .evalTap(doRun(_, effect))
              }
        }
    }
  }

}
