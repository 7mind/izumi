package com.github.pshirshov.izumi.idealingua.compiler

import java.io.File
import java.nio.file.Path

import scopt.OptionParser

case class LanguageOpts(id: String, withRuntime: Boolean, manifest: Option[File], extensions: List[String])


case class IDLCArgs(source: Path, target: Path, languages: List[LanguageOpts])

object IDLCArgs {
  val parser: OptionParser[IDLCArgs] = new scopt.OptionParser[IDLCArgs]("idlc") {
    head("idlc")
    help("help")

    opt[File]('s', "source").required().valueName("<dir>")
      .action((a, c) => c.copy(source = a.toPath))
      .text("source directory")

    opt[File]('t', "target").required().valueName("<dir>")
      .action((a, c) => c.copy(target = a.toPath))
      .text("target directory")

    arg[String]("language-id")
      .text("{scala|typescript|go|csharp}")
      .action {
        (a, c) =>
          c.copy(languages = c.languages :+ LanguageOpts(a, withRuntime = true, None, List.empty))
      }
      .optional()
      .unbounded()
      .children(
        opt[File]("manifest").abbr("m")
          .optional()
          .text("manifest file to parse to the language-specific compiler module compiler. Use `@` builting stub, `+` for default path (./manifests/<language>.json)")
          .action {
            (a, c) =>
              c.copy(languages = c.languages.init :+ c.languages.last.copy(manifest = Some(a)))
          },
        opt[Unit]("no-runtime").abbr("nrt")
          .optional()
          .text("don't include buitin runtime into compiler output")
          .action {
            (_, c) =>
              c.copy(languages = c.languages.init :+ c.languages.last.copy(withRuntime = false))
          },
        opt[String]("extensions").abbr("e")
          .optional()
          .text("extensions spec, like -AnyvalExtension;-CirceDerivationTranslatorExtension or *")
          .action {
            (a, c) =>
              c.copy(languages = c.languages.init :+ c.languages.last.copy(extensions = a.split(',').toList))
          },
      )
  }

}
