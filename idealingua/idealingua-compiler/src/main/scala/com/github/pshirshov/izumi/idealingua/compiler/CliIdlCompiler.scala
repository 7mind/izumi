package com.github.pshirshov.izumi.idealingua.compiler

import java.io.File
import java.nio.file._

import com.github.pshirshov.izumi.fundamentals.platform.time.Timed
import com.github.pshirshov.izumi.idealingua.il.loader.LocalModelLoader
import com.github.pshirshov.izumi.idealingua.translator._
import com.github.pshirshov.izumi.idealingua.translator.togolang.GoLangTranslator
import com.github.pshirshov.izumi.idealingua.translator.toscala.{CirceDerivationTranslatorExtension, ScalaTranslator}
import com.github.pshirshov.izumi.idealingua.translator.totypescript.TypeScriptTranslator
import scopt.OptionParser

object CliIdlCompiler {

  case class CliArgs(source: Path, target: Path, languages: Map[String, String])

  private val parser: OptionParser[CliArgs] = new scopt.OptionParser[CliArgs]("idlc") {
    head("idlc")

    opt[File]('s', "source").required().valueName("<dir>")
      .action((x, c) => c.copy(source = x.toPath))
      .text("source directory")

    opt[File]('t', "target").required().valueName("<dir>")
      .action((x, c) => c.copy(source = x.toPath))
      .text("target directory")


    opt[(String, String)]("lang")
      .minOccurs(0)
      .unbounded()
      .action({
        case ((k, v), c) => c.copy(languages = c.languages.updated(k, v))
      })
      .keyValueName("<language>", "<extspec>")
      .text("languages to use and rules, like --lang:scala=-AnyvalExtension;-CirceDerivationTranslatorExtension --lang:go=*")
  }

  private def extensions: Map[IDLLanguage, Seq[TranslatorExtension]] = Map(
    IDLLanguage.Scala -> (ScalaTranslator.defaultExtensions ++ Seq(CirceDerivationTranslatorExtension))
    , IDLLanguage.Typescript -> TypeScriptTranslator.defaultExtensions
    , IDLLanguage.Go -> GoLangTranslator.defaultExtensions
  )

  private def getExt(lang: IDLLanguage, filter: String): Seq[TranslatorExtension] = {
    val all = extensions(lang)
    val parts = filter.split(";").map(_.trim)
    val negative = parts.filter(_.startsWith("-")).map(_.substring(1)).map(ExtensionId).toSet
    all.filterNot(e => negative.contains(e.id))
  }

  def main(args: Array[String]): Unit = {
    val default = CliArgs(
      Paths.get("source")
      , Paths.get("target")
      , Map.empty
    )
    val conf = parser.parse(args, default) match {
      case Some(c) =>
        c
      case _ =>
        parser.showUsage()
        throw new IllegalArgumentException(s"Unexpected commandline")
    }

    val languages = if (conf.languages.nonEmpty) {
      conf.languages
    } else {
      Map("scala" -> "*", "go" -> "*", "typescript" -> "*")
    }

    val options = languages.map {
      case (name, ext) =>
        val lang = IDLLanguage.parse(name)
        TypespaceCompiler.CompilerOptions(lang, getExt(lang, ext))
    }

    val path = conf.source
    val target = conf.target
    println(s"Targets")
    options.foreach {
      o =>
        val e = o.extensions.map(_.id)
        println(s"${o.language}")
        println(s"  ${e.mkString(",")}")
    }

    println(s"Loading definitions from `$path`...")
    val toCompile = Timed {
      new LocalModelLoader(path, Seq.empty).load()
    }
    println(s"Done: ${toCompile.size} in ${toCompile.duration.toMillis}ms")
    println()

    options.foreach {
      option =>
        val itarget = target.resolve(option.language.toString)

        val out = Timed {
          new IDLCompiler(toCompile)
            .compile(target, option)
        }

        val allPaths = out.invokation.flatMap(_._2.paths)

        println(s"${allPaths.size} source files from ${out.invokation.size} domains produced in `$itarget` in ${out.duration.toMillis}ms")
        println(s"Stubs  : ${out.stubs.files.size} ${option.language.toString} files copied")
        println(s"Archive: ${out.sources}")
    }
  }


}
