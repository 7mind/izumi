package com.github.pshirshov.izumi.distage.roles.impl

import java.io.File

import com.github.pshirshov.izumi.distage.model.definition.{BindingTag, ModuleBase}
import com.github.pshirshov.izumi.distage.roles.launcher.RoleAppBootstrapStrategy.Using
import com.github.pshirshov.izumi.logstage.api.Log

final case class RoleAppBootstrapStrategyArgs(
                                               disabledTags: BindingTag.Expressions.Expr
                                               , roleSet: Set[String]
                                               , jsonLogging: Boolean
                                               , rootLogLevel: Log.Level
                                               , using: Seq[Using]
                                               , addOverrides: ModuleBase
                                               , primaryConfig: Option[File]
                                               , roleConfigs: Map[String, File]
                                               , dumpContext: Boolean
                                             )
