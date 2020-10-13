package com.github.pshirshov.test.plugins

import izumi.distage.plugins.{PluginConfig, PluginDef}
import izumi.distage.roles.RoleAppMain
import izumi.distage.roles.model.definition.RoleModuleDef

object StaticTestMain extends RoleAppMain.LauncherIdentity {
  override protected def pluginConfig: PluginConfig = PluginConfig.cached("com.github.pshirshov.test.plugins") ++ new PluginDef with RoleModuleDef {
    makeRole[StaticTestRole]
    makeRole[DependingRole]
  }
}
