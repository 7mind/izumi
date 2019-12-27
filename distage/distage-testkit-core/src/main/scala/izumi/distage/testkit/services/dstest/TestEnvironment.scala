package izumi.distage.testkit.services.dstest

import distage._
import izumi.distage.framework.model.ActivationInfo
import izumi.distage.model.definition.Activation
import izumi.distage.roles.model.meta.RolesInfo

final case class TestEnvironment(
                                  bsModule: ModuleBase,
                                  appModule: ModuleBase,
                                  roles: RolesInfo,
                                  activationInfo: ActivationInfo,
                                  activation: Activation,
                                  memoizedKeys: DIKey => Boolean,
                                )
