package izumi.distage.framework.services

import distage.{BootstrapModuleDef, Injector, PlannerInput}
import izumi.distage.framework.config.PlanningOptions
import izumi.distage.framework.services.RoleAppPlanner.AppStartupPlans
import izumi.distage.model.definition.{Activation, BootstrapModule, Id, ModuleBase}
import izumi.distage.model.plan.{Plan, Roots}
import izumi.distage.model.recursive.{BootConfig, Bootloader}
import izumi.distage.model.reflection.DIKey
import izumi.functional.quasi.{QuasiAsync, QuasiIO, QuasiIORunner}
import izumi.fundamentals.platform.functional.Identity
import izumi.logstage.api.IzLogger
import izumi.reflect.TagK

trait RoleAppPlanner {
  def bootloader: Bootloader

//  def reboot(bsModule: BootstrapModule, config: Option[AppConfig]): RoleAppPlanner
  def makePlan(appMainRoots: Set[DIKey]): AppStartupPlans
}

object RoleAppPlanner {

  final case class AppStartupPlans(
    runtime: Plan,
    app: Plan,
    injector: Injector[Identity],
  )

  class Impl[F[_]: TagK](
    options: PlanningOptions,
    activation: Activation @Id("roleapp"),
    bsModule: BootstrapModule @Id("roleapp"),
    val bootloader: Bootloader @Id("roleapp"),
    logger: IzLogger,
//    parser: ActivationParser,
  ) /*(implicit
    defaultModule: DefaultModule[F]
  )*/
    extends RoleAppPlanner { self =>

    private[this] val runtimeGcRoots: Set[DIKey] = Set(
      DIKey.get[QuasiIORunner[F]],
      DIKey.get[QuasiIO[F]],
      DIKey.get[QuasiAsync[F]],
    )

//    override def reboot(bsOverride: BootstrapModule, config: Option[AppConfig]): RoleAppPlanner = {
//      val configOverride = new BootstrapModuleDef {
//        config.foreach(cfg => include(AppConfigModule(cfg)))
//      }
//      val updatedBsModule = bsModule overriddenBy bsOverride overriddenBy configOverride
//
//      val activation = config.map(parser.parseActivation).getOrElse(this.activation)
//
//      new RoleAppPlanner.Impl[F](options, activation, updatedBsModule, bootloader, logger, parser)
//    }

    override def makePlan(appMainRoots: Set[DIKey]): AppStartupPlans = {
      logger.trace(s"Application will use: ${appMainRoots -> "app roots"} and $activation")

      val out = for {
        bootstrapped <- bootloader.boot(
          BootConfig(
            bootstrap = _ =>
              bsModule overriddenBy new BootstrapModuleDef {
                make[RoleAppPlanner].fromValue(self)
              },
            activation = _ => activation,
            roots = _ => Roots(runtimeGcRoots),
          )
        )

        runtimeKeys = bootstrapped.plan.keys
        _ <- Right {
          logger.trace(s"Bootstrap plan:\n${bootstrapped.plan.render() -> "bootstrap dump" -> null}")
          logger.trace(s"App module: ${(bootstrapped.module: ModuleBase) -> "app module" -> null}")
        }
        appPlan <- bootstrapped.injector.plan(PlannerInput(bootstrapped.module.drop(runtimeKeys), activation, appMainRoots))
      } yield {

        val check = new PlanCircularDependencyCheck(options, logger)

        check.showProxyWarnings(bootstrapped.plan)
        check.showProxyWarnings(appPlan)

        logger.info(s"Planning finished. ${appPlan.keys.size -> "main ops"} ${bootstrapped.plan.keys.size -> "runtime ops"}")
        logger.debug(s"Plan:\n${appPlan.render() -> "plan dump" -> null}")

        AppStartupPlans(bootstrapped.plan, appPlan, bootstrapped.injector)
      }

      out.getOrThrow()
    }

  }

}
