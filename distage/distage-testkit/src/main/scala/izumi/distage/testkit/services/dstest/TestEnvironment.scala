package izumi.distage.testkit.services.dstest

import izumi.distage.roles.model.AppActivation
import izumi.distage.roles.model.meta.RolesInfo
import distage._

case class TestEnvironment(
                            bsModule: ModuleBase,
                            appModule: ModuleBase,
                            roles: RolesInfo,
                            activation: AppActivation,
                            memoize: DIKey => Boolean = Set.empty
                          )
