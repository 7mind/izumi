package com.github.pshirshov.izumi.distage.roles.impl

import java.io.File

import com.github.pshirshov.izumi.distage.roles.launcher.ConfigWriter.WriteReference
import com.github.pshirshov.izumi.distage.roles.launcher.RoleArgs
import com.github.pshirshov.izumi.fundamentals.platform.resources.IzManifest
import com.github.pshirshov.izumi.logstage.api.{IzLogger, Log}
import scopt.{OptionDef, OptionParser}

trait Initializable[T] {
  implicit def init : T
}


class ScoptLauncherArgs(
                         var configFile: Option[File] = None
                         , var writeReference: Option[WriteReference] = None
                         , var dummyStorage: Option[Boolean] = Some(false)
                         , var rootLogLevel: Log.Level = Log.Level.Info
                         , var jsonLogging: Option[Boolean] = Some(false)
                         , var dumpContext: Option[Boolean] = Some(false)
                         , var roles: List[RoleArgs] = List.empty
                       )


class IzumiScoptLauncherArgs(configFile: Option[File] = None
                             ,writeReference: Option[WriteReference] = None
                             ,dummyStorage: Option[Boolean] = Some(false)
                             ,rootLogLevel: Log.Level = Log.Level.Info
                             ,jsonLogging: Option[Boolean] = Some(false)
                             ,dumpContext: Option[Boolean] = Some(false)
                             ,roles: List[RoleArgs] = List.empty) extends ScoptLauncherArgs

object IzumiScoptLauncherArgs {
  implicit val init : Initializable[IzumiScoptLauncherArgs] = new Initializable[IzumiScoptLauncherArgs] {
    override implicit def init: IzumiScoptLauncherArgs = new IzumiScoptLauncherArgs()
  }
}

// TODO: this stuff needs to be refactored, we can't keep WriteReference here
object ScoptLauncherArgs {

  private final val parserName = "izumi-launcher"


  class IzParser[T <: ScoptLauncherArgs] extends OptionParser[T](parserName) {
      def getOptions: List[OptionDef[_, T]] = {
        this.options.toList
      }
  }

  class IzOptionParser[T <: ScoptLauncherArgs](parserExtension: Set[IzParser[T]]) extends IzParser[T] {
    private val version = IzManifest.manifest[this.type]()
      .map(IzManifest.read)
      .map(_.version.version)
      .getOrElse("UNDEFINED")


    head("tg-launcher", s"Manifest version: $version")
    help("help")

    opt[Unit]("logs-json").abbr("lj")
      .text("turn on json logging")
      .action { (_, c) =>
        c.jsonLogging = Some(true)
        c
      }

    opt[Unit]("dump-graph").abbr("dg")
      .text("dump object graph")
      .action { (_, c) =>
        c.dumpContext = Some(true)
        c
      }

    opt[Unit]("dummy-storage").abbr("ds")
      .text("use in-memory dummy storages instead of production ones")
      .action { (_, c) =>
        c.dummyStorage = Some(true)
        c
      }

    opt[Log.Level]("root-log-level").abbr("rll")
      .text("Root logging level")
      .valueName("log level")
      .action { (v, c) =>
        c.rootLogLevel = v
        c
      }

    opt[File]('c', "config")
      .text("configuration file")
      .valueName("<common config file>")
      .action {
        case (x, c) =>
          c.configFile = Some(x)
          c
      }

    opt[Unit]("write-reference").abbr("wr").action((_, c) => {
      c.writeReference = Some(WriteReference())
      c
    }).text("dump reference config in HOCON format")
      .children(
        opt[Unit]("json").abbr("js").action((_, c) => {
          c.writeReference = Some(WriteReference(asJson = true))
          c
        }).text("dump reference config in json format"),
        opt[Boolean]("include-common").abbr("ic").action((b, c) => {
          c.writeReference = Some(WriteReference(includeCommon = b))
          c
        }).text("include common part in role configs"),
        opt[Boolean]("use-launcher-version").abbr("lv").action((b, c) => {
          c.writeReference = Some(WriteReference(useLauncherVersion = b))
          c
        }).text("use launcher version instead of role version"),
        opt[String]("targetDir").abbr("d").action((dir, c) => {
          c.writeReference = Some(WriteReference(targetDir = dir))
          c
        }).text("folder to store reference configs, ./config by default")
      )

    arg[String]("<role>...").unbounded().optional()
      .text("roles to enable")
      .action {
        (a, c) =>
          c.roles = c.roles :+ RoleArgs(a, None)
          c
      }
      .children(
        opt[String]("role-id").abbr("i")
          .text("role id to enable (legacy option, just put role name as argument)")
          .action { case (a, c) =>
            c.roles = c.roles.init :+ c.roles.last.copy(name = a)
            c
          },

        opt[File]("role-config").abbr("rc").optional()
          .text("config file for role, it will override the common config file")
          .action { case (f, c) =>
            c.roles = c.roles.init :+ c.roles.last.copy(configFile = Some(f))
            c
          },
      )

    this.options ++= parserExtension.flatMap(_.getOptions)
  }

  object IzOptionParser {
    class Base extends IzOptionParser[ScoptLauncherArgs](Set.empty)
  }

  implicit lazy val logLevelRead: scopt.Read[Log.Level] = scopt.Read.reads[Log.Level] {
    v =>
      IzLogger.parseLevel(v)
  }
}


