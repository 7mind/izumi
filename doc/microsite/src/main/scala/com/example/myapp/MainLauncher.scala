package com.example.myapp

import izumi.distage.plugins.{PluginConfig, PluginDef}
import izumi.distage.roles.RoleAppMain
import izumi.distage.roles.model.{RoleDescriptor, RoleTask}
import izumi.distage.roles.model.definition.RoleModuleDef
import izumi.fundamentals.platform.cli.model.raw.RawEntrypointParams
import izumi.fundamentals.platform.functional.Identity

object MainLauncher extends RoleAppMain.LauncherIdentity {
  override protected def pluginConfig: PluginConfig = PluginConfig.const(new PluginDef with RoleModuleDef {
    makeRole[ExampleRole]
  })
}

class ExampleRole extends RoleTask[Identity] {
  override def start(roleParameters: RawEntrypointParams, freeArgs: Vector[String]): Identity[Unit] =
    println("hello wolrd")
}
object ExampleRole extends RoleDescriptor {
  override def id: String = "examplerole"
}
