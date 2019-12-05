package izumi.distage.testkit.services.dstest

import izumi.distage.roles.model.AppActivation
import distage._
import izumi.distage.model.planning.PlanMergingPolicy
import izumi.distage.roles.meta.RolesInfo
import izumi.distage.roles.services.PruningPlanMergingPolicy

case class TestEnvironment(
                            private val baseBsModule: ModuleBase,
                            appModule: ModuleBase,
                            roles: RolesInfo,
                            activation: AppActivation,
                            memoizedKeys: DIKey => Boolean,
                          ) {
  def bsModule: ModuleBase = baseBsModule overridenBy new BootstrapModuleDef {
    make[PlanMergingPolicy].from[PruningPlanMergingPolicy]
    make[AppActivation].from(activation)
  }
}
