package com.github.pshirshov.izumi.idealingua.compiler

import java.io.File
import java.nio.file.{Path, Paths}

import com.github.pshirshov.izumi.fundamentals.platform.cli.CLIParser._
import com.github.pshirshov.izumi.fundamentals.platform.cli.{CLIParser, ParserDef, ParserFailureHandler}

case class LanguageOpts(
                         id: String,
                         withRuntime: Boolean,
                         manifest: Option[File],
                         credentials: Option[File],
                         extensions: List[String],
                         overrides: Map[String, String],
                       )


case class IDLCArgs(
                     source: Path,
                     overlay: Path,
                     target: Path,
                     languages: List[LanguageOpts],
                     init: Option[Path],
                     versionOverlay: Option[Path],
                     overrides: Map[String, String],
                     publish: Boolean = false
                   )

object IDLCArgs {
  def default: IDLCArgs = IDLCArgs(
    Paths.get("source")
    , Paths.get("overlay")
    , Paths.get("target")
    , List.empty
    , None
    , None
    , Map.empty
  )

  object P extends ParserDef {
    final val initTarget = arg("init", "i", "project in directory", "<path>")
    final val sourceDir = arg("source", "s", "source directory", "<path>")
    final val targetDir = arg("target", "t", "target directory", "<path>")
    final val overlayDir = arg("overlay", "o", "overlay directory", "<path>")
    final val overlayVersionFile = arg("overlay-version", "v", "version file", "<path>")
    final val define = arg("define", "d", "define value", "const.name=value")
    final val manifest = arg("manifest", "m", "manifest file",  "<path>")
    final val credentials = arg("credentials", "cr", "credentials file", "<path>")
    final val extensionSpec = arg("extensions", "e", "extensions spec", "{* | -AnyvalExtension;-CirceDerivationTranslatorExtension}")
    final val publish = flag("publish", "p", "build and publish generated code")
    final val noRuntime = flag("disable-runtime", "nr", "don't include builtin runtime")
  }


  def parseUnsafe(args: Array[String]): IDLCArgs = {
    val parsed = new CLIParser().parse(args) match {
      case Left(value) =>
        ParserFailureHandler.TerminatingHandler.onParserError(value)
      case Right(value) =>
        value
    }

    if (parsed.roles.isEmpty || parsed.roles.exists(_.role == "help")) {
      ParserDef.formatOptions(P).foreach(println)
    }

    val init = parsed.roles.find(_.role == "init").flatMap {
      r =>
        P.initTarget.findValue(r.roleParameters).asPath
    }

    val parameters = parsed.globalParameters
    val src = P.sourceDir.findValue(parameters).asPath.getOrElse(Paths.get("./source"))
    val target = P.sourceDir.findValue(parameters).asPath.getOrElse(Paths.get("./target"))
    val overlay = P.overlayDir.findValue(parameters).asPath.getOrElse(Paths.get("./overlay"))
    val overlayVersion = P.overlayVersionFile.findValue(parameters).asPath
    val publish = P.publish.hasFlag(parameters)
    val defines = P.define.findValues(parameters).map(v => v.name -> v.value).toMap

    val languages = parsed.roles.filterNot(_.role == "init").map {
      role =>
        val parameters = role.roleParameters
        val runtime = !P.noRuntime.hasFlag(parameters)
        val manifest = P.manifest.findValue(parameters).asFile
        val credentials = P.credentials.findValue(parameters).asFile
        val defines = P.define.findValues(parameters).map {
          v =>
            val parts = v.value.split('=')
            parts.head -> parts.tail.mkString("=")
        }.toMap
        val extensions = P.extensionSpec.findValue(parameters).map(_.value.split(',')).toList.flatten

        LanguageOpts(
          role.role,
          runtime,
          manifest,
          credentials,
          extensions,
          defines
        )
    }

    IDLCArgs(
      src,
      overlay,
      target,
      languages.toList,
      init,
      overlayVersion,
      defines,
      publish
    )
  }



  //  val parser: OptionParser[IDLCArgs] = new scopt.OptionParser[IDLCArgs]("idlc") {
  //    head("idlc")
  //    help("help")
  //
  //    opt[File]('i', "init").optional().valueName("<dir>")
  //      .action((a, c) => c.copy(init = Some(a.toPath)))
  //      .text("init directory (must be empty or non-existing)")
  //
  //    opt[Unit]('p', "publish").optional()
  //      .action((_, c) => c.copy(publish = true))
  //      .text("publish compiled files to repos")
  //
  //    opt[String]('g', "global-define").valueName("name=value")
  //      .text("Define global manifest override")
  //      .optional()
  //      .unbounded()
  //      .action {
  //        (a, c) =>
  //          val (k, v) = a.splitFirst('=')
  //          c.copy(overrides = c.overrides.updated(k, v))
  //      }
  //
  //    opt[File]('v', "version-overlay").optional().valueName("<version.json>")
  //      .action((a, c) => c.copy(versionOverlay = Some(a.toPath)))
  //      .text("path to version overlay file (version.json)")
  //
  //    opt[File]('s', "source").optional().valueName("<dir>")
  //      .action((a, c) => c.copy(source = a.toPath))
  //      .text("source directory (default: `./source`)")
  //
  //    opt[File]('o', "overlay").optional().valueName("<dir>")
  //      .action((a, c) => c.copy(overlay = a.toPath))
  //      .text("overlay model (default: `./overlay`)")
  //
  //    opt[File]('t', "target").optional().valueName("<dir>")
  //      .action((a, c) => c.copy(target = a.toPath))
  //      .text("target directory (default: `./target`)")
  //
  //    arg[String]("**language-id**")
  //      .text("{scala|typescript|go|csharp} (may repeat, like `scala -mf + typescript -mf + -nrt go`")
  //      .action {
  //        (a, c) =>
  //          c.copy(languages = c.languages :+ LanguageOpts(a, withRuntime = true, None, None, List.empty, Map.empty))
  //      }
  //      .optional()
  //      .unbounded()
  //      .children(
  //        opt[File]("credentials").abbr("cr")
  //          .optional()
  //          .text("Language-specific credentials file")
  //          .action {
  //            (a, c) =>
  //              c.copy(languages = c.languages.init :+ c.languages.last.copy(credentials = Some(a)))
  //          },
  //        opt[File]("manifest").abbr("m")
  //          .optional()
  //          .text("Language-specific compiler manifest. Use `@` for builtin stub, `+` for default path (./manifests/<language>.json)")
  //          .action {
  //            (a, c) =>
  //              c.copy(languages = c.languages.init :+ c.languages.last.copy(manifest = Some(a)))
  //          },
  //        opt[Unit]("no-runtime").abbr("nrt")
  //          .optional()
  //          .text("Don't include buitin runtime into compiler output")
  //          .action {
  //            (_, c) =>
  //              c.copy(languages = c.languages.init :+ c.languages.last.copy(withRuntime = false))
  //          },
  //        opt[String]('d', "define").valueName("name=value")
  //          .text("Define manifest override")
  //          .optional()
  //          .unbounded()
  //          .action {
  //            (a, c) =>
  //              val (k, v) = a.splitFirst('=')
  //              c.copy(languages = c.languages.init :+ c.languages.last.copy(overrides = c.languages.last.overrides.updated(k, v)))
  //          },
  //        opt[String]("extensions").abbr("e").valueName("spec")
  //          .optional()
  //          .text("extensions spec, like -AnyvalExtension;-CirceDerivationTranslatorExtension or *")
  //          .action {
  //            (a, c) =>
  //              c.copy(languages = c.languages.init :+ c.languages.last.copy(extensions = a.split(',').toList))
  //          },
  //      )
  //  }

}
