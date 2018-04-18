package com.github.pshirshov.izumi.idealingua.compiler

import java.nio.file.{Path, Paths}

import com.github.pshirshov.izumi.fundamentals.platform.files.IzFiles
import com.github.pshirshov.izumi.fundamentals.platform.time.Timed
import com.github.pshirshov.izumi.idealingua.il.loader.LocalModelLoader
import com.github.pshirshov.izumi.idealingua.translator.IDLCompiler.IDLSuccess
import com.github.pshirshov.izumi.idealingua.translator.toscala.{CirceDerivationTranslatorExtension, ScalaTranslator}
import com.github.pshirshov.izumi.idealingua.translator.totypescript.TypeScriptTranslator
import com.github.pshirshov.izumi.idealingua.translator.{ExtensionId, IDLCompiler, IDLLanguage, TranslatorExtension}
import org.rogach.scallop.{ScallopConf, ScallopOption}


class Conf(arguments: Seq[String]) extends ScallopConf(arguments) {
  val source: ScallopOption[Path] = opt[Path](name = "source", default = Some(Paths.get("source")), descr = "Input directory")
  val target: ScallopOption[Path] = opt[Path](name = "target", default = Some(Paths.get("target")), descr = "Output directory")

  val languages: Map[String, String] = props[String]('L', descr = "Key is language id, value is extension filter. Example: -L scala=-AnyvalExtension;-CirceDerivationTranslatorExtension")


  verify()

}

object CliIdlCompiler {
  private def extensions: Map[IDLLanguage, Seq[TranslatorExtension]] = Map(
    IDLLanguage.Scala -> (ScalaTranslator.defaultExtensions ++ Seq(CirceDerivationTranslatorExtension))
    , IDLLanguage.Typescript -> TypeScriptTranslator.defaultExtensions
  )

  private def getExt(lang: IDLLanguage, filter: String): Seq[TranslatorExtension] = {
    val all = extensions(lang)
    val parts = filter.split(";").map(_.trim)
    val negative = parts.filter(_.startsWith("-")).map(_.substring(1)).map(ExtensionId).toSet
    all.filterNot(e => negative.contains(e.id))
  }

  def main(args: Array[String]): Unit = {
    val conf = new Conf(args)

    val languages = conf.languages

    val options = languages.map {
      case (name, ext) =>
        val lang = IDLLanguage.parse(name)
        IDLCompiler.CompilerOptions(lang, getExt(lang, ext))
    }

    val path = conf.source()
    val target = conf.target()
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


    toCompile.value.foreach {
      domain =>
        println(s"Processing domain ${domain.id}...")
        val compiler = new IDLCompiler(domain)
        options.foreach {
          option =>
            val itarget = target.resolve(option.language.toString)

            val out = Timed {
              IzFiles.remove(itarget)
              print(s"  - Compiling into ${option.language}: ")
              compiler.compile(itarget, option) match {
                case s: IDLSuccess =>
                  s

                case _ =>
                  throw new IllegalStateException(s"Cannot compile model ${domain.id}")
              }
            }

            println(s"${out.paths.size} source files produced in `$itarget` in ${out.duration.toMillis}ms")
        }

    }
  }

}
