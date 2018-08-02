package com.github.pshirshov.izumi.distage.roles.launcher.test

import com.github.pshirshov.izumi.distage.plugins.PluginDef
import com.github.pshirshov.izumi.distage.roles.roles.RoleService

class TestPlugin extends PluginDef {
  make[RoleService].named("testservice").from[TestService]
}
