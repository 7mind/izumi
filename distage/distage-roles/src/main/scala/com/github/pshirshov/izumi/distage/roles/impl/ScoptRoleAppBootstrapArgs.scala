package com.github.pshirshov.izumi.distage.roles.impl

import com.github.pshirshov.izumi.distage.model.definition.{ModuleBase, ModuleDef}
import com.github.pshirshov.izumi.distage.roles.impl.ScoptLauncherArgs.WriteReference
import com.github.pshirshov.izumi.distage.roles.launcher.RoleApp
import com.github.pshirshov.izumi.distage.roles.launcher.RoleAppBootstrapStrategy.Using
import com.github.pshirshov.izumi.distage.roles.roles.BackendPluginTags
import com.github.pshirshov.izumi.fundamentals.tags.TagExpr
import com.github.pshirshov.izumi.logstage.api.Log

final case class ScoptRoleAppBootstrapArgs(
                                            disabledTags: TagExpr.Strings.Expr
                                          , roleSet: Set[String]
                                          , jsonLogging: Boolean
                                          , rootLogLevel: Log.Level
                                          , using: Seq[Using]
                                          , addOverrides: ModuleBase
                                          )

object ScoptRoleAppBootstrapArgs {

  def apply(params: ScoptLauncherArgs): ScoptRoleAppBootstrapArgs =
    ScoptRoleAppBootstrapArgs(
      disabledTags =
        if (params.dummyStorage.contains(true)) {
          TagExpr.Strings.all(BackendPluginTags.Production, BackendPluginTags.Storage)
        } else {
          TagExpr.Strings.any(BackendPluginTags.Test, BackendPluginTags.Dummy)
        }
      , roleSet =
          if (params.writeReference.isDefined) {
            params.roles.map(_.name).toSet + "configwriter" // FIXME coupling
          } else {
            params.roles.map(_.name).toSet
          }
      , jsonLogging = params.jsonLogging.getOrElse(false)
      , rootLogLevel = params.rootLogLevel
      , using = Seq(Using("distage-roles", classOf[RoleApp]))
      , addOverrides = new ModuleDef {
        make[WriteReference].from(params.writeReference.getOrElse(WriteReference()))
      }
    )

}
