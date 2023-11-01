package com.github.pshirshov.test4

import distage.Mode
import izumi.distage.Subcontext
import izumi.distage.model.definition.ModuleDef
import izumi.distage.plugins.{PluginConfig, PluginDef}
import izumi.distage.roles.RoleAppMain
import izumi.distage.roles.model.definition.RoleModuleDef
import izumi.distage.roles.model.{RoleDescriptor, RoleTask}
import izumi.fundamentals.platform.cli.model.raw.RawEntrypointParams
import izumi.fundamentals.platform.functional.Identity

object Fixture4 {

  object TestMainBad extends RoleAppMain.LauncherIdentity {
    override protected def pluginConfig: PluginConfig = PluginConfig.const(BadModule)
  }

  object TestMainGood extends RoleAppMain.LauncherIdentity {
    override protected def pluginConfig: PluginConfig = PluginConfig.const(GoodModule)
  }

  object BadModule extends PluginDef with RoleModuleDef {
    makeSubcontext[Dep](
      new ModuleDef {
        make[Dep].tagged(Mode.Prod).from[DepGood]
        make[Dep].tagged(Mode.Test).from[DepBad]
      }
    )

    makeRole[TargetRole]
  }

  object GoodModule extends PluginDef with RoleModuleDef {
    makeSubcontext[Dep](
      new ModuleDef {
        make[Dep].tagged(Mode.Prod).from[DepGood]
        make[Dep].tagged(Mode.Test).from[DepBad]
      }
    ).localDependency[MissingDep]

    makeRole[TargetRole]
  }

  class TargetRole(
    val depCtx: Subcontext[Dep]
  ) extends RoleTask[Identity] {
    def mkDep(): Dep = {
      depCtx
        .provide[MissingDep](new MissingDep {})
        .produceRun(identity)
    }

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
