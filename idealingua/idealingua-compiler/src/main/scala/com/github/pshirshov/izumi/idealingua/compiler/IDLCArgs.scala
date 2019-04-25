package com.github.pshirshov.izumi.idealingua.compiler

import java.io.File
import java.nio.file.{Path, Paths}

import com.github.pshirshov.izumi.fundamentals.platform.cli.CLIParser._
import com.github.pshirshov.izumi.fundamentals.platform.cli.ParserDef.ParserRoleDescriptor
import com.github.pshirshov.izumi.fundamentals.platform.cli.{CLIParser, Parameters, ParserDef, ParserFailureHandler}

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
    final val sourceDir = arg("source", "s", "source directory", "<path>")
    final val targetDir = arg("target", "t", "target directory", "<path>")
    final val overlayDir = arg("overlay", "o", "overlay directory", "<path>")
    final val overlayVersionFile = arg("overlay-version", "v", "version file", "<path>")
    final val define = arg("define", "d", "define value", "const.name=value")
    final val publish = flag("publish", "p", "build and publish generated code")


  }

  object LP extends ParserDef {
    final val manifest = arg("manifest", "m", "manifest file", "<path>")
    final val credentials = arg("credentials", "cr", "credentials file", "<path>")
    final val extensionSpec = arg("extensions", "e", "extensions spec", "{* | -AnyvalExtension;-CirceDerivationTranslatorExtension}")
    final val noRuntime = flag("disable-runtime", "nr", "don't include builtin runtime")
    final val define = arg("define", "d", "define value", "const.name=value")
  }

  object IP extends ParserDef {}

  def parseUnsafe(args: Array[String]): IDLCArgs = {
    val parsed = new CLIParser().parse(args) match {
      case Left(value) =>
        ParserFailureHandler.TerminatingHandler.onParserError(value)
      case Right(value) =>
        value
    }

    val roleHelp = ParserDef.formatRoles(Seq(
      ParserRoleDescriptor("init", LP, Some("setup project template")),
      ParserRoleDescriptor("scala", LP, Some("scala target")),
      ParserRoleDescriptor("go", LP, Some("go target")),
      ParserRoleDescriptor("csharp", LP, Some("C#/Unity target")),
      ParserRoleDescriptor("typescript", LP, Some("Typescript target")),
    ))

    if (parsed.roles.isEmpty || parsed.roles.exists(_.role == "help")) {
      for {
        gh <- ParserDef.formatOptions(P)
      } yield {
        import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
        val fullHelp =
          s"""Global options:
             |${gh.shift(2)}
             |
             |Supported commands:
             |${roleHelp.shift(2)}
           """.stripMargin
        println(fullHelp)

      }
    }

    val init = parsed.roles.find(_.role == "init").map {
      r =>
        new File(r.freeArgs.head).toPath
    }

    val parameters = parsed.globalParameters
    val src = P.sourceDir.findValue(parameters).asPath.getOrElse(Paths.get("./source"))
    val target = P.targetDir.findValue(parameters).asPath.getOrElse(Paths.get("./target"))
    assert(src.toFile.getCanonicalPath != target.toFile.getCanonicalPath)
    val overlay = P.overlayDir.findValue(parameters).asPath.getOrElse(Paths.get("./overlay"))
    val overlayVersion = P.overlayVersionFile.findValue(parameters).asPath
    val publish = P.publish.hasFlag(parameters)
    val defines = parseDefs(parameters)

    val internalRoles = Seq("init", "help")

    val languages = parsed.roles.filterNot(r => internalRoles.contains(r.role)).map {
      role =>
        val parameters = role.roleParameters
        val runtime = !LP.noRuntime.hasFlag(parameters)
        val manifest = LP.manifest.findValue(parameters).asFile
        val credentials = LP.credentials.findValue(parameters).asFile
        val defines = parseDefs(parameters)
        val extensions = LP.extensionSpec.findValue(parameters).map(_.value.split(',')).toList.flatten

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

  private def parseDefs(parameters: Parameters) = {
    LP.define.findValues(parameters).map {
      v =>
        val parts = v.value.split('=')
        parts.head -> parts.tail.mkString("=")
    }.toMap
  }

}
