package com.github.pshirshov.izumi.distage.roles.launcher.test

import com.github.pshirshov.izumi.distage.plugins.PluginDef
import com.github.pshirshov.izumi.distage.roles.roles.RoleService

trait NotCloseable

class InheritedCloseable extends NotCloseable with AutoCloseable {
  override def close(): Unit = {}
}

class TestPlugin extends PluginDef {
  make[RoleService].named("testservice").from[TestService]
  many[Dummy]
  make[NotCloseable].from[InheritedCloseable]
}
