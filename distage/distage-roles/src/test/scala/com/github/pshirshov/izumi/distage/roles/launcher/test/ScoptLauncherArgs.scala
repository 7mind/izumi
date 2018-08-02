package com.github.pshirshov.izumi.distage.roles.launcher.test

import java.io.File

import com.github.pshirshov.izumi.distage.roles.launcher.RoleArgs
import com.github.pshirshov.izumi.distage.roles.launcher.test.ScoptLauncherArgs.WriteReference
import com.github.pshirshov.izumi.logstage.api.Log
import scopt.OptionParser

case class ScoptLauncherArgs(
                           configFile: Option[File] = None
                           , writeReference: Option[WriteReference] = None
                           , dummyStorage: Option[Boolean] = Some(false)
                           , rootLogLevel: Log.Level = Log.Level.Info
                           , jsonLogging: Option[Boolean] = Some(false)
                           , roles: List[RoleArgs] = List.empty
                         )

object ScoptLauncherArgs {

  case class WriteReference(asJson: Boolean = false
                            , targetDir: String = "config"
                            , includeCommon: Boolean = true)

  val parser: OptionParser[ScoptLauncherArgs] = new scopt.OptionParser[ScoptLauncherArgs]("tg-launcher") {
     head("tg-launcher", "TODO: manifest version")
     help("help")

     opt[Unit]("logs-json").abbr("lj")
       .text("turn on json logging")
       .action { (_, c) =>
         c.copy(jsonLogging = Some(true))
       }

     opt[Unit]("dummy-storage").abbr("ds")
       .text("use in-memory dummy storages instead of production ones")
       .action { (_, c) =>
         c.copy(dummyStorage = Some(true))
       }

     opt[Log.Level]("root-log-level").abbr("rll")
       .text("Root logging level")
       .action { (v, c) =>
         c.copy(rootLogLevel = v)
       }

     opt[File]('c', "config")
       .text("configuration file")
       .valueName("<common config file>")
       .action {
         case (x, c) =>
           c.copy(configFile = Some(x))
       }

     opt[Unit]("write-reference").abbr("wr").action((_, c) =>
       c.copy(writeReference = Some(WriteReference()))).text("dump reference config in HOCON format")
       .children(
         opt[Unit]("json").abbr("js").action((_, c) =>
           c.copy(writeReference = Some(WriteReference(asJson = true)))
         ).text("dump reference config in json format"),
         opt[Boolean]("include-common").abbr("ic").action((b, c) =>
           c.copy(writeReference = Some(WriteReference(includeCommon = b)))
         ).text("include common part in role configs"),
         opt[String]("targetDir").abbr("d").action((dir, c) =>
           c.copy(writeReference = Some(WriteReference(targetDir = dir)))
         ).text("folder to store reference configs, ./config by default")
       )

     arg[String]("<role>...").unbounded().optional()
       .text("roles to enable")
       .action {
         (a, c) =>
           c.copy(roles = c.roles :+ RoleArgs(a, None))
       }
       .children(
         opt[String]("role-id").abbr("i")
           .text("role id to enable (legacy option, just put role name as argument)")
           .action { case (a, c) =>
             c.copy(roles = c.roles.init :+ c.roles.last.copy(name = a))
           },

         opt[File]("role-config").abbr("rc").optional()
           .text("config file for role, it will override the common config file")
           .action { case (f, c) =>
             c.copy(roles = c.roles.init :+ c.roles.last.copy(configFile = Some(f)))
           },
       )
   }

   implicit val logLevelRead: scopt.Read[Log.Level] = scopt.Read.reads[Log.Level] {
     v =>
       v.charAt(0).toLower match {
         case 't' => Log.Level.Trace
         case 'd' => Log.Level.Debug
         case 'i' => Log.Level.Info
         case 'w' => Log.Level.Warn
         case 'e' => Log.Level.Error
         case 'c' => Log.Level.Crit
       }
   }

}
