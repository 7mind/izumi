package com.github.pshirshov.izumi.distage.roles.launcher.test

import com.github.pshirshov.izumi.distage.plugins.PluginDef
import com.github.pshirshov.izumi.distage.roles.roles.{BackendPluginTags, RoleService}

trait NotCloseable

class InheritedCloseable extends NotCloseable with AutoCloseable {
  override def close(): Unit = {}
}

class TestPlugin extends PluginDef {
  tag(BackendPluginTags.Production)
  make[RoleService].named("testservice").from[TestService]
  many[Dummy]
  make[NotCloseable].from[InheritedCloseable]
}
