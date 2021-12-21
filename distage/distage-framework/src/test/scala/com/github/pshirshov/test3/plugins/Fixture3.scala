package com.github.pshirshov.test3.plugins

import com.github.pshirshov.test3.bootstrap.BootstrapFixture3.{BasicConfig, BootstrapComponent, UnsatisfiedDep}
import izumi.distage.model.definition
import izumi.distage.model.definition.{Activation, Id, ModuleDef}
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

  object TestRoleAppMainFailing extends RoleAppMain.LauncherIdentity {
    override protected def pluginConfig: PluginConfig = PluginConfig.cachedThisPkg
    override protected def bootstrapPluginConfig: PluginConfig = PluginConfig.cached("com.github.pshirshov.test3.bootstrap") overriddenBy new PluginDef {
      modify[BootstrapComponent].addDependency[UnsatisfiedDep]
    }
  }

  object TestRoleAppMainCircularAppModule extends RoleAppMain.LauncherIdentity {
    override protected def pluginConfig: PluginConfig = PluginConfig.cachedThisPkg ++ new PluginDef {
      modify[BasicConfig].withDependencies {
        (_: Fixture3Role) => (BasicConfig: BasicConfig) => BasicConfig
      }
    }
    override protected def bootstrapPluginConfig: PluginConfig = PluginConfig.cached("com.github.pshirshov.test3.bootstrap")
  }

  object TestRoleAppMainCircularRoleAppBootModule extends RoleAppMain.LauncherIdentity {
    override protected def pluginConfig: PluginConfig = PluginConfig.cachedThisPkg
    override protected def bootstrapPluginConfig: PluginConfig = PluginConfig.cached("com.github.pshirshov.test3.bootstrap")

    override protected def roleAppBootOverrides(argv: RoleAppMain.ArgV): definition.Module = new ModuleDef {
      modify[Activation].named("default").withDependencies {
        (_: Activation @Id("default")) => self => self
      }
    }
  }

  final class TestPlugin extends PluginDef with RoleModuleDef {
    makeRole[Fixture3Role]
  }

  class Fixture3Role(
    val basicConfig: BasicConfig
    // There is no direct dependency on BootstrapComponent anywhere, however, since it's in bootstrap, it's always a Root
//    val bootstrapComponent: BootstrapComponent
  ) extends RoleTask[Identity] {
    override def start(roleParameters: RawEntrypointParams, freeArgs: Vector[String]): Unit = ()
  }
  object Fixture3Role extends RoleDescriptor {
    final val id = "fixture3"
  }

}
