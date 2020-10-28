package com.github.pshirshov.test2.plugins

import izumi.distage.model.definition.StandardAxis.{Mode, Repo}
import izumi.distage.plugins.{PluginConfig, PluginDef}
import izumi.distage.roles.RoleAppMain
import izumi.distage.roles.model.definition.RoleModuleDef
import izumi.distage.roles.model.{RoleDescriptor, RoleTask}
import izumi.fundamentals.platform.cli.model.raw.RawEntrypointParams
import izumi.fundamentals.platform.functional.Identity

object Fixture {

  object TestRoleAppMain2 extends RoleAppMain.LauncherIdentity {
    override protected def pluginConfig: PluginConfig = PluginConfig.cached("com.github.pshirshov.test2.plugins")
  }

  final class TestPlugin2 extends PluginDef with RoleModuleDef {
    make[Dep1].tagged(Mode.Prod, Repo.Prod).from[Dep1Good]
    make[Dep1].tagged(Mode.Prod, Repo.Dummy).from[Dep1Good]
    make[Dep1].tagged(Mode.Test, Repo.Prod).from[Dep1Bad]
    make[Dep1].tagged(Mode.Test, Repo.Dummy).from[Dep1Bad]

    makeRole[TargetRole]
  }

  class TargetRole(
    val dep1: Dep1
  ) extends RoleTask[Identity] {
    override def start(roleParameters: RawEntrypointParams, freeArgs: Vector[String]): Unit = ()
  }

  object TargetRole extends RoleDescriptor {
    final val id = "target"
  }

  trait Dep1
  class Dep1Good extends Dep1
  class Dep1Bad(
    val missingDep: MissingDep
  ) extends Dep1

  trait MissingDep

}
