package com.github.pshirshov.izumi.distage.testkit.services.dstest

import com.github.pshirshov.izumi.distage.roles.model.AppActivation
import com.github.pshirshov.izumi.distage.roles.model.meta.RolesInfo
import distage._

case class TestEnvironment(
                            bsModule: ModuleBase,
                            appModule: ModuleBase,
                            roles: RolesInfo,
                            activation: AppActivation,
                          )
