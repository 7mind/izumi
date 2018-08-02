package com.github.pshirshov.izumi.distage.roles.launcher.test

import com.github.pshirshov.izumi.distage.plugins.PluginDef
import com.github.pshirshov.izumi.distage.roles.roles.RoleAppService

class TestPlugin extends PluginDef {
  make[RoleAppService].named("testservice").from[TestService]
}
