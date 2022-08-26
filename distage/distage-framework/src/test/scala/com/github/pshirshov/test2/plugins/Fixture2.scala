package com.github.pshirshov.test2.plugins

import izumi.distage.model.definition.StandardAxis.{Mode, Repo}
import izumi.distage.plugins.{PluginConfig, PluginDef}
import izumi.distage.roles.RoleAppMain
import izumi.distage.roles.model.definition.RoleModuleDef
import izumi.distage.roles.model.{RoleDescriptor, RoleTask}
import izumi.fundamentals.platform.cli.model.raw.RawEntrypointParams
import izumi.fundamentals.platform.functional.Identity

object Fixture2 {

  object TestRoleAppMain extends RoleAppMain.LauncherIdentity {
    override protected def pluginConfig: PluginConfig = PluginConfig.cached("com.github.pshirshov.test2.plugins")
  }

  final class TestPlugin extends PluginDef() with RoleModuleDef {
    make[Dep].tagged(Mode.Prod, Repo.Prod).from[DepGood]
    make[Dep].tagged(Mode.Prod, Repo.Dummy).from[DepGood]

    make[Dep].tagged(Mode.Test, Repo.Prod).from[DepBad]
    make[Dep].tagged(Mode.Test, Repo.Dummy).from[DepBad]

    makeRole[TargetRole].tagged(Repo.Prod)
    makeRole[TargetRole].tagged(Repo.Dummy)
  }

  class TargetRole(
    val dep: Dep
  ) extends RoleTask[Identity] {
    override def start(roleParameters: RawEntrypointParams, freeArgs: Vector[String]): Unit = ()
  }

  object TargetRole extends RoleDescriptor {
    final val id = "target"
  }

  trait Dep
  class DepGood extends Dep
  class DepBad(
    val missingDep: MissingDep
  ) extends Dep

  trait MissingDep

}
