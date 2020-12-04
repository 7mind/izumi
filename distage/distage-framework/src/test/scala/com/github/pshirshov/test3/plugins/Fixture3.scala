package com.github.pshirshov.test3.plugins

import com.github.pshirshov.test3.bootstrap.BootstrapFixture3.BasicConfig
import izumi.distage.plugins.{PluginConfig, PluginDef}
import izumi.distage.roles.RoleAppMain
import izumi.distage.roles.model.definition.RoleModuleDef
import izumi.distage.roles.model.{RoleDescriptor, RoleTask}
import izumi.fundamentals.platform.cli.model.raw.RawEntrypointParams
import izumi.fundamentals.platform.functional.Identity

object Fixture3 {

  object TestRoleAppMain extends RoleAppMain.LauncherIdentity {
    override protected def pluginConfig: PluginConfig = PluginConfig.cachedThisPkg
    override protected def bootstrapPluginConfig: PluginConfig = PluginConfig.cached("com.github.pshirshov.test3.bootstrap")
  }

  final class TestPlugin extends PluginDef with RoleModuleDef {
    makeRole[Fixture3Role]
  }

  class Fixture3Role(
    val basicConfig: BasicConfig
  ) extends RoleTask[Identity] {
    override def start(roleParameters: RawEntrypointParams, freeArgs: Vector[String]): Unit = ()
  }

  object Fixture3Role extends RoleDescriptor {
    final val id = "fixture3"
  }

}
