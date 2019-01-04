package com.github.pshirshov.izumi.idealingua.compiler

import java.io.File
import java.nio.file._

import com.github.pshirshov.izumi.fundamentals.platform.files.IzFiles
import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
import com.github.pshirshov.izumi.fundamentals.platform.time.Timed
import com.github.pshirshov.izumi.idealingua.il.loader.{LocalModelLoaderContext, ModelResolver}
import com.github.pshirshov.izumi.idealingua.model.loader.UnresolvedDomains
import com.github.pshirshov.izumi.idealingua.model.publishing.manifests._
import com.github.pshirshov.izumi.idealingua.translator._
import io.circe.{Decoder, Encoder}
import org.scalacheck._
import org.scalacheck.rng.Seed

import scala.reflect._
import scala.util.{Failure, Success, Try}


object CommandlineIDLCompiler extends ScalacheckShapeless with Codecs {
  implicit val sgen: Arbitrary[String] = Arbitrary(Gen.alphaLowerStr)


  def main(args: Array[String]): Unit = {
    val conf = parseArgs(args)
    val toRun = conf.languages.map(toOption)

    println("We are going to run:")
    println(toRun.niceList())
    println()

    val path = conf.source.toAbsolutePath
    val target = conf.target.toAbsolutePath
    target.toFile.mkdirs()

    println(s"Loading definitions from `$path`...")
    val loaded = Timed {
      val context = new LocalModelLoaderContext(path, Seq.empty)
      context.loader.load()
    }
    println(s"Done: ${loaded.value.domains.results.size} in ${loaded.duration.toMillis}ms")
    println()

    toRun.foreach {
      option =>
        runCompiler(target, loaded, option)

    }
  }

  private def runCompiler(target: Path, loaded: Timed[UnresolvedDomains], option: UntypedCompilerOptions): Unit = {
    val langId = option.language.toString
    val itarget = target.resolve(langId)
    println(s"Preparing typespace for $langId")
    val toCompile = Timed {
      val rules = TypespaceCompilerBaseFacade.descriptor(option.language).rules
      new ModelResolver(rules)
        .resolve(loaded.value)
        .ifWarnings {
          message =>
            println(message)
        }
        .ifFailed {
          message =>
            println(message)
            System.exit(1)
        }
        .successful
    }
    println(s"Finished in ${toCompile.duration.toMillis}ms")

    val out = Timed {
      new TypespaceCompilerFSFacade(toCompile)
        .compile(itarget, option)
    }

    val allPaths = out.compilationProducts.flatMap(_.paths)

    println(s"${allPaths.size} source files from ${out.compilationProducts.size} domains produced in `$itarget` in ${out.duration.toMillis}ms")
    println(s"Archive: ${out.zippedOutput}")
    println("")
  }

  private def parseArgs(args: Array[String]) = {
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
        throw new IllegalArgumentException("Unexpected commandline")
    }
    conf
  }

  private def toOption(lopt: LanguageOpts) = {
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

  private def readManifest[T: Arbitrary : ClassTag : Decoder : Encoder](path: File): T = {
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
    val descriptor = TypespaceCompilerBaseFacade.descriptor(lang)
    val negative = filter.filter(_.startsWith("-")).map(_.substring(1)).map(ExtensionId).toSet
    descriptor.defaultExtensions.filterNot(e => negative.contains(e.id))
  }
}



