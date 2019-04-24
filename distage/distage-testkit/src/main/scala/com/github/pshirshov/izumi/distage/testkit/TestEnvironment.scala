package com.github.pshirshov.izumi.distage.testkit

import com.github.pshirshov.izumi.distage.roles.model.meta.RolesInfo
import distage.ModuleBase

case class TestEnvironment(
                            bsModule: ModuleBase,
                            appModule: ModuleBase,
                            roles: RolesInfo,
                          )
