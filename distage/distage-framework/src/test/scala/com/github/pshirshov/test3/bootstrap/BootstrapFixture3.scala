package com.github.pshirshov.test3.bootstrap

import distage.plugins.BootstrapPluginDef
import izumi.distage.config.ConfigModuleDef

object BootstrapFixture3 {

  final case class BasicConfig(a: Boolean, b: Int)

  object BootstrapPlugin extends BootstrapPluginDef with ConfigModuleDef {
    makeConfig[BasicConfig]("basicConfig")
  }

}
