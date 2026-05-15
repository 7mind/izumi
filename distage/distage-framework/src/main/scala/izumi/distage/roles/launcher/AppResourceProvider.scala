package izumi.distage.roles.launcher

import distage.TagKK
import izumi.distage.InjectorFactory
import izumi.distage.framework.services.RoleAppPlanner.AppStartupPlans
import izumi.distage.model.Locator
import izumi.distage.model.definition.Lifecycle
import izumi.distage.model.provisioning.PlanInterpreter.FinalizerFilter
import izumi.distage.roles.launcher.AppResourceProvider.AppResource
import izumi.functional.bio.{Async2, Bifunctorized, IO2, Primitives2, UnsafeRun2}

trait AppResourceProvider[F[+_, +_]] {
  def makeAppResource: AppResource[F]
}

object AppResourceProvider {

  final case class AppResource[F[+_, +_]](resource: Lifecycle[Bifunctorized.IdentityBifunctorized, Throwable, PreparedApp[F]]) extends AnyVal

  final case class FinalizerFilters[F[+_, +_]](
    filterF: FinalizerFilter[F],
    filterId: FinalizerFilter[Bifunctorized.IdentityBifunctorized],
  )

  object FinalizerFilters {
    def all[F[+_, +_]]: FinalizerFilters[F] = FinalizerFilters[F](FinalizerFilter.all, FinalizerFilter.all)
  }

  class Impl[F[+_, +_]: TagKK: IO2: Primitives2](
    entrypoint: RoleAppEntrypoint[F],
    filters: FinalizerFilters[F],
    appPlan: AppStartupPlans,
    injectorFactory: InjectorFactory,
    hook: AppShutdownStrategy[F],
  ) extends AppResourceProvider[F] {
    def makeAppResource: AppResource[F] = AppResource {
      appPlan.injector
        .produceFX[Bifunctorized.IdentityBifunctorized](appPlan.runtime, filters.filterId)
        .map {
          runtimeLocator =>
            val runner = runtimeLocator.get[UnsafeRun2[F]]
            val F = runtimeLocator.get[IO2[F]]
            val FA = runtimeLocator.get[Async2[F]]
            PreparedApp(prepareMainResource(runtimeLocator)(F), entrypoint, runner, F, FA)
        }
    }

    private def prepareMainResource(runtimeLocator: Locator)(implicit F: IO2[F]): Lifecycle[F, Throwable, Locator] = {
      injectorFactory
        .inherit(runtimeLocator)
        .produceFX[F](appPlan.app, filters.filterF)
        .wrapRelease((r, a) => F.guarantee(r(a), F.sync(hook.finishShutdown())))
    }
  }

}
