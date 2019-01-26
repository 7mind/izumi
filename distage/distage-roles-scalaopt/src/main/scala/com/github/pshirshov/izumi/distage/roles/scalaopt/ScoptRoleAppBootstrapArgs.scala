package com.github.pshirshov.izumi.distage.roles.scalaopt

import com.github.pshirshov.izumi.distage.model.definition.{BindingTag, ModuleDef}
import com.github.pshirshov.izumi.distage.roles.impl.RoleAppBootstrapStrategyArgs
import com.github.pshirshov.izumi.distage.roles.launcher.ConfigWriter.WriteReference
import com.github.pshirshov.izumi.distage.roles.launcher.RoleApp
import com.github.pshirshov.izumi.distage.roles.launcher.RoleAppBootstrapStrategy.Using

object ScoptRoleAppBootstrapArgs {

  def apply[T <: ScoptLauncherArgs](params: T, disabledTags: BindingTag.Expressions.Expr): RoleAppBootstrapStrategyArgs =
    RoleAppBootstrapStrategyArgs(
      disabledTags = disabledTags
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
      , dumpContext = params.dumpContext.getOrElse(false)
    )
}
