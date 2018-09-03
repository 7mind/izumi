package com.github.pshirshov.izumi.distage.roles.impl

import com.github.pshirshov.izumi.distage.model.definition.ModuleDef
import com.github.pshirshov.izumi.distage.roles.impl.ScoptLauncherArgs.WriteReference
import com.github.pshirshov.izumi.distage.roles.launcher.RoleApp
import com.github.pshirshov.izumi.distage.roles.launcher.RoleAppBootstrapStrategy.Using
import com.github.pshirshov.izumi.distage.roles.roles.BackendPluginTags
import com.github.pshirshov.izumi.fundamentals.tags.TagExpr

// TODO
object ScoptRoleAppBootstrapArgs {

  def apply(params: ScoptLauncherArgs): RoleAppBootstrapStrategyArgs =
    RoleAppBootstrapStrategyArgs(
      disabledTags =
        if (params.dummyStorage.contains(true)) {
          TagExpr.Strings.all(BackendPluginTags.Production, BackendPluginTags.Storage)
        } else {
          TagExpr.Strings.any(BackendPluginTags.Test, BackendPluginTags.Dummy)
        }
      , roleSet =
          if (params.writeReference.isDefined) {
            params.roles.map(_.name).toSet + "configwriter" // TODO coupling
          } else {
            params.roles.map(_.name).toSet
          }
      , jsonLogging = params.jsonLogging.getOrElse(false)
      , rootLogLevel = params.rootLogLevel
      , using = Seq(Using("distage-roles", classOf[RoleApp]))
      , addOverrides = new ModuleDef {
        make[WriteReference].from(params.writeReference.getOrElse(WriteReference()))
      }
      , primaryConfig = params.configFile
      , roleConfigs = params.roles.flatMap(r => r.configFile.map(c => r.name -> c).toSeq).toMap
    )

}
