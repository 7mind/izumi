package com.github.pshirshov.izumi.idealingua.compiler

import java.nio.file._

import com.github.pshirshov.izumi.fundamentals.platform.files.IzFiles
import com.github.pshirshov.izumi.fundamentals.platform.time.Timed
import com.github.pshirshov.izumi.idealingua.il.loader.LocalModelLoader
import com.github.pshirshov.izumi.idealingua.translator.IDLCompiler.IDLSuccess
import com.github.pshirshov.izumi.idealingua.translator.toscala.{CirceDerivationTranslatorExtension, ScalaTranslator}
import com.github.pshirshov.izumi.idealingua.translator.totypescript.TypeScriptTranslator
import com.github.pshirshov.izumi.idealingua.translator.togolang.GoLangTranslator
import com.github.pshirshov.izumi.idealingua.translator._
import org.rogach.scallop.{ScallopConf, ScallopOption}


class Conf(arguments: Seq[String]) extends ScallopConf(arguments) {
  val source: ScallopOption[Path] = opt[Path](name = "source", default = Some(Paths.get("source")), descr = "Input directory")
  val target: ScallopOption[Path] = opt[Path](name = "target", default = Some(Paths.get("target")), descr = "Output directory")
  val runtime: ScallopOption[Boolean] = opt[Boolean](name = "runtime", descr = "Copy embedded runtime", default = Some(false))

  val languages: Map[String, String] = props[String]('L', descr = "Key is language id, value is extension filter. Example: -L scala=-AnyvalExtension;-CirceDerivationTranslatorExtension")


  verify()
}

// TODO: XXX: io is shitty here

object CliIdlCompiler {
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

    options.foreach {
      option =>
        val itarget = target.resolve(option.language.toString)

        val out = Timed {
          new CompilerIvokation(toCompile)
            .compile(target, option)
        }

        val allPaths = out.invokation.flatMap {
          case (_, s: IDLSuccess) =>
            s.paths

          case (id, failure) =>
            throw new IllegalStateException(s"Cannot compile model $id: $failure")
        }

        val ztarget = target.resolve(s"${option.language.toString}.zip")
        zip(ztarget, enumerate(itarget))

        println(s"${allPaths.size} source files from ${out.invokation.size} domains produced in `$itarget` in ${out.duration.toMillis}ms")
        println(s"Stubs: ${out.stubs.count} ${option.language.toString} files copied")
    }
  }


  import java.nio.file.{Files, Path}

  import scala.collection.JavaConverters._

  private def enumerate(src: Path): List[ZE] = {
    val files = Files.walk(src).iterator()
      .asScala
      .filter(_.toFile.isFile).toList

    files.map(f => {
      ZE(src.relativize(f).toString, f)
    })
  }

  final case class ZE(name: String, file: Path)

  def zip(out: Path, files: Iterable[ZE]): Unit = {
    import java.io.{BufferedInputStream, FileInputStream, FileOutputStream}
    import java.util.zip.{ZipEntry, ZipOutputStream}

    val outFile = out.toFile
    if (outFile.exists()) {
      outFile.delete()
    }

    val zip = new ZipOutputStream(new FileOutputStream(outFile))

    files.foreach { name =>
      zip.putNextEntry(new ZipEntry(name.name))
      val in = new BufferedInputStream(new FileInputStream(name.file.toFile))
      var b = in.read()
      while (b > -1) {
        zip.write(b)
        b = in.read()
      }
      in.close()
      zip.closeEntry()
    }
    zip.close()
  }
}
