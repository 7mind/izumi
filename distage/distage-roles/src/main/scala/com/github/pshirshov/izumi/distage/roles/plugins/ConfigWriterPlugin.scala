package com.github.pshirshov.izumi.distage.roles.plugins

import com.github.pshirshov.izumi.distage.plugins.PluginDef
import com.github.pshirshov.izumi.distage.roles.launcher.ConfigWriter
import com.github.pshirshov.izumi.distage.roles.roles.RoleAppService

class ConfigWriterPlugin extends PluginDef {
  make[RoleAppService].named("configwriter").from[ConfigWriter]
}
