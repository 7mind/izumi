package izumi.distage.roles.services

import distage.{BootstrapModule, DIKey, Injector, TagK, _}
import izumi.distage.model.definition.{ModuleBase, ModuleDef}
import izumi.distage.model.monadic.{DIEffect, DIEffectAsync, DIEffectRunner}
import izumi.distage.model.plan.{OrderedPlan, TriSplittedPlan}
import izumi.distage.roles.config.ContextOptions
import izumi.distage.roles.model.{AppActivation, IntegrationCheck}
import izumi.distage.roles.services.RoleAppPlanner.AppStartupPlans
import izumi.logstage.api.IzLogger

trait RoleAppPlanner[F[_]] {
  def reboot(bsModule: BootstrapModule): RoleAppPlanner[F]
  def makePlan(appMainRoots: Set[DIKey], appModule: ModuleBase): AppStartupPlans
}

object RoleAppPlanner {
  case class AppStartupPlans(
                              runtime: OrderedPlan,
                              app: TriSplittedPlan,
                              injector: Injector,
                            )

  class Impl[F[_]: TagK](
                          options: ContextOptions,
                          bsModule: BootstrapModule,
                          activation: AppActivation,
                          logger: IzLogger,
                        ) extends RoleAppPlanner[F] { self =>

    private val injector = Injector.Standard(bsModule)

    override def reboot(bsOverride: BootstrapModule): RoleAppPlanner[F] = {
      new RoleAppPlanner.Impl[F](options, bsModule overridenBy bsOverride, activation, logger)
    }

    override def makePlan(appMainRoots: Set[DIKey], appModule: ModuleBase): AppStartupPlans = {
      val fullAppModule = appModule
        .overridenBy(new ModuleDef {
          make[RoleAppPlanner[F]].from(self)
          make[ContextOptions].from(options)
          make[ModuleBase].named("application.module").from(appModule)
        })

      val runtimeGcRoots: Set[DIKey] = Set(
        DIKey.get[DIEffectRunner[F]],
        DIKey.get[DIEffect[F]],
        DIKey.get[DIEffectAsync[F]],
      )

      val runtimePlan = injector.plan(PlannerInput(fullAppModule, runtimeGcRoots))

      val appPlan = injector.trisectByKeys(fullAppModule.drop(runtimeGcRoots), appMainRoots) {
        _.collectChildren[IntegrationCheck].map(_.target).toSet
      }

      val check = new PlanCircularDependencyCheck(options, logger)
      check.verify(runtimePlan)
      check.verify(appPlan.shared.plan)
      check.verify(appPlan.side.plan)
      check.verify(appPlan.primary.plan)

//      import izumi.fundamentals.platform.strings.IzString._
//      logger.error(s"${fullAppModule.bindings.niceList() -> "app module"}")
//      logger.error(s"${runtimePlan.render() -> "runtime plan"}")
//      logger.error(s"${appPlan.shared.plan.render() -> "shared plan"}")
//      logger.error(s"${appPlan.side.plan.render() -> "integration plan"}")
//      logger.error(s"${appPlan.primary.plan.render() -> "primary plan"}")

      AppStartupPlans(
        runtimePlan,
        appPlan,
        injector
      )
    }
  }

}
