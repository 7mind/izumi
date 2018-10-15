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

class ResourcesPlugin extends PluginDef {
  make[InitCounter]

  make[Resource1]
  make[Resource2]
  make[Resource3]
  make[Resource4]
  make[Resource5]
  make[Resource6]

  many[Resource]
    .ref[Resource1]
    .ref[Resource2]
    .ref[Resource3]
    .ref[Resource4]
    .ref[Resource5]
    .ref[Resource6]
}
