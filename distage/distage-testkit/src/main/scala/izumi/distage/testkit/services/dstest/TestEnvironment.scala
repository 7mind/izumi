package izumi.distage.testkit.services.dstest

import izumi.distage.roles.model.AppActivation
import izumi.distage.roles.model.meta.RolesInfo
import distage._
import izumi.distage.model.planning.PlanMergingPolicy
import izumi.distage.roles.services.PruningPlanMergingPolicy

case class TestEnvironment(
                            baseBsModule: ModuleBase,
                            appModule: ModuleBase,
                            roles: RolesInfo,
                            activation: AppActivation,
                            memoize: DIKey => Boolean = Set.empty
                          ) {
  def bsModule: ModuleBase = baseBsModule overridenBy new BootstrapModuleDef {
    make[PlanMergingPolicy].from[PruningPlanMergingPolicy]
    make[AppActivation].from(activation)
  }
}
