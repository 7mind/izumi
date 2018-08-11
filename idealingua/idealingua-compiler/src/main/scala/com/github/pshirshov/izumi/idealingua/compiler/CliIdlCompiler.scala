package com.github.pshirshov.izumi.idealingua.compiler

import java.io.File
import java.nio.file._

import com.github.pshirshov.izumi.fundamentals.platform.files.IzFiles
import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
import com.github.pshirshov.izumi.fundamentals.platform.time.Timed
import com.github.pshirshov.izumi.idealingua.il.loader.LocalModelLoader
import com.github.pshirshov.izumi.idealingua.model.publishing.manifests._
import com.github.pshirshov.izumi.idealingua.model.publishing.{ManifestDependency, Publisher}
import com.github.pshirshov.izumi.idealingua.translator.TypespaceCompiler._
import com.github.pshirshov.izumi.idealingua.translator._
import com.github.pshirshov.izumi.idealingua.translator.tocsharp.CSharpTranslator
import com.github.pshirshov.izumi.idealingua.translator.togolang.GoLangTranslator
import com.github.pshirshov.izumi.idealingua.translator.toscala.ScalaTranslator
import com.github.pshirshov.izumi.idealingua.translator.toscala.extensions.CirceDerivationTranslatorExtension
import com.github.pshirshov.izumi.idealingua.translator.totypescript.TypeScriptTranslator
import io.circe.{Decoder, Encoder}
import org.scalacheck._
import org.scalacheck.rng.Seed
import scopt.OptionParser

import scala.reflect._
import scala.util.{Failure, Success, Try}

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
          .text("manifest file to parse to the language-specific compiler module compiler")
          .action {
            (a, c) =>
              c.copy(languages = c.languages.init :+ c.languages.last.copy(manifest = Some(a)))
          },
        opt[Unit]("no-runtime").abbr("nrt")
          .optional()
          .text("don't include runtime into compiler output")
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

object CliIdlCompiler extends ScalacheckShapeless with Codecs {
  implicit val sgen: Arbitrary[String] = Arbitrary(Gen.alphaLowerStr)

  private def extensions: Map[IDLLanguage, Seq[TranslatorExtension]] = Map(
    IDLLanguage.Scala -> (ScalaTranslator.defaultExtensions ++ Seq(CirceDerivationTranslatorExtension))
    , IDLLanguage.Typescript -> TypeScriptTranslator.defaultExtensions
    , IDLLanguage.Go -> GoLangTranslator.defaultExtensions
    , IDLLanguage.CSharp -> CSharpTranslator.defaultExtensions
  )


  def main(args: Array[String]): Unit = {
    val default = IDLCArgs(
      Paths.get("source")
      , Paths.get("target")
      , List.empty
    )
    val conf = IDLCArgs.parser.parse(args, default) match {
      case Some(c) =>
        c
      case _ =>
        IDLCArgs.parser.showUsage()
        throw new IllegalArgumentException(s"Unexpected commandline")
    }

    val toRun = conf.languages.map {
      lopt =>
        val lang = IDLLanguage.parse(lopt.id)
        val exts = getExt(lang, lopt.extensions)

        val manifest = lang match {
          case IDLLanguage.Scala =>
            lopt.manifest.map(readManifest[ScalaBuildManifest])
          case IDLLanguage.Typescript =>
            lopt.manifest.map(readManifest[TypeScriptBuildManifest])
          case IDLLanguage.Go =>
            lopt.manifest.map(readManifest[GoLangBuildManifest])
          case IDLLanguage.CSharp =>
            lopt.manifest.map(readManifest[CSharpBuildManifest])
        }

        UntypedCompilerOptions(lang, exts, lopt.withRuntime, manifest)
    }

    println("We are going to run:")
    println(toRun.niceList())
    println()

    val path = conf.source.toAbsolutePath
    val target = conf.target.toAbsolutePath
    target.toFile.mkdirs()

    println(s"Loading definitions from `$path`...")
    val toCompile = Timed {
      new LocalModelLoader(path, Seq.empty).load()
    }
    println(s"Done: ${toCompile.size} in ${toCompile.duration.toMillis}ms")
    println()

    toRun.foreach {
      option =>
        val langId = option.language.toString
        println(s"Working on $langId")
        val itarget = target.resolve(langId)

        val out = Timed {
          new IDLCompiler(toCompile)
            .compile(itarget, option)
        }

        val allPaths = out.compilationProducts.flatMap(_._2.paths)

        println(s"${allPaths.size} source files from ${out.compilationProducts.size} domains produced in `$itarget` in ${out.duration.toMillis}ms")
        println(s"Archive: ${out.zippedOutput}")
        println("")

    }
  }

  def readManifest[T : Arbitrary : ClassTag : Decoder : Encoder](path: File): T = {
    import _root_.io.circe.parser._
    import _root_.io.circe.syntax._
    Try(parse(IzFiles.readString(path)).flatMap(_.as[T])) match {
      case Success(Right(r)) =>
        r
      case o =>
        val errRepr = o match {
          case Success(Left(l)) =>
            l.toString
          case Failure(f) =>
            f.toString
          case e =>
            e.toString
        }
        println(s"Failed to read manifest from $path: $errRepr")
        println(s"Example manifest file for ${classTag[T].runtimeClass}:")
        println(implicitly[Arbitrary[T]].arbitrary.pureApply(Gen.Parameters.default, Seed.random()).asJson)
        System.out.flush()
        System.exit(1)
        throw new IllegalArgumentException(s"Failed to load manifest from $path: $errRepr")
    }
  }

  private def getExt(lang: IDLLanguage, filter: List[String]): Seq[TranslatorExtension] = {
    val all = extensions(lang)
    val negative = filter.filter(_.startsWith("-")).map(_.substring(1)).map(ExtensionId).toSet
    all.filterNot(e => negative.contains(e.id))
  }
}

trait Codecs {

  import _root_.io.circe._
  import _root_.io.circe.generic.semiauto._
  import _root_.io.circe.generic.extras.semiauto

  implicit def decMdep: Decoder[ManifestDependency] = deriveDecoder

  implicit def decPublisher: Decoder[Publisher] = deriveDecoder

  implicit def decTsModuleSchema: Decoder[TypeScriptModuleSchema] = semiauto.deriveEnumerationDecoder

  implicit def decScala: Decoder[ScalaBuildManifest] = deriveDecoder

  implicit def decTs: Decoder[TypeScriptBuildManifest] = deriveDecoder

  implicit def decGo: Decoder[GoLangBuildManifest] = deriveDecoder

  implicit def decCs: Decoder[CSharpBuildManifest] = deriveDecoder


  implicit def encMdep: Encoder[ManifestDependency] = deriveEncoder

  implicit def encPublisher: Encoder[Publisher] = deriveEncoder

  implicit def encTsModuleSchema: Encoder[TypeScriptModuleSchema] = semiauto.deriveEnumerationEncoder

  implicit def encScala: Encoder[ScalaBuildManifest] = deriveEncoder

  implicit def encTs: Encoder[TypeScriptBuildManifest] = deriveEncoder

  implicit def encGo: Encoder[GoLangBuildManifest] = deriveEncoder

  implicit def encCs: Encoder[CSharpBuildManifest] = deriveEncoder
}

