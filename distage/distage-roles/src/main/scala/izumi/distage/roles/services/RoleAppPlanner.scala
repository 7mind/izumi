package izumi.distage.roles.services

import izumi.distage.model.definition.ModuleBase
import izumi.distage.model.plan.OrderedPlan
import izumi.distage.roles.services.RoleAppPlanner.AppStartupPlans
import distage.{BootstrapModule, DIKey, Injector}

trait RoleAppPlanner[F[_]] {
  def reboot(bsModule: BootstrapModule): RoleAppPlanner[F]
  def makePlan(appMainRoots: Set[DIKey], appModule: ModuleBase): AppStartupPlans
}

object RoleAppPlanner {
  case class AppStartupPlans(
                              runtime: OrderedPlan,
                              integration: OrderedPlan,
                              integrationKeys: Set[DIKey],
                              app: OrderedPlan,
                              injector: Injector
                            )
}
