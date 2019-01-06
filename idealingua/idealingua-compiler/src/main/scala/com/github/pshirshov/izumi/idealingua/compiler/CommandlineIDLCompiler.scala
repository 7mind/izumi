package com.github.pshirshov.izumi.idealingua.compiler

import java.nio.file._

import com.github.pshirshov.izumi.fundamentals.platform.resources.IzManifest
import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
import com.github.pshirshov.izumi.fundamentals.platform.time.Timed
import com.github.pshirshov.izumi.idealingua.il.loader.{LocalModelLoaderContext, ModelResolver}
import com.github.pshirshov.izumi.idealingua.model.loader.UnresolvedDomains
import com.github.pshirshov.izumi.idealingua.translator._
import com.typesafe.config.ConfigFactory
import io.circe.Json

import scala.collection.JavaConverters._


object CommandlineIDLCompiler {
  private val log = CompilerLog.Default

  def main(args: Array[String]): Unit = {
    val mf = IzManifest.manifest[CommandlineIDLCompiler.type]().map(IzManifest.read)
    val izumiVersion = mf.map(_.version.toString).getOrElse("0.0.0-UNKNOWN")
    val izumiInfoVersion = mf.map(_.justVersion).getOrElse("UNKNOWN-BUILD")

    log.log(s"Izumi IDL Compiler $izumiInfoVersion")

    val conf = parseArgs(args)
    val toRun = conf.languages.map(toOption(Map("common.izumiVersion" -> izumiVersion)))


    log.log("We are going to run:")
    log.log(toRun.niceList())
    log.log("")

    val path = conf.source.toAbsolutePath
    val target = conf.target.toAbsolutePath
    target.toFile.mkdirs()

    log.log(s"Loading definitions from `$path`...")
    val loaded = Timed {
      val context = new LocalModelLoaderContext(path, Seq.empty)
      context.loader.load()
    }
    log.log(s"Done: ${loaded.value.domains.results.size} in ${loaded.duration.toMillis}ms")
    log.log("")

    toRun.foreach {
      option =>
        runCompiler(target, loaded, option)

    }
  }

  private def runCompiler(target: Path, loaded: Timed[UnresolvedDomains], option: UntypedCompilerOptions): Unit = {
    val langId = option.language.toString
    val itarget = target.resolve(langId)
    log.log(s"Preparing typespace for $langId")
    val toCompile = Timed {
      val rules = TypespaceCompilerBaseFacade.descriptor(option.language).rules
      new ModelResolver(rules)
        .resolve(loaded.value)
        .ifWarnings {
          message =>
            log.log(message)
        }
        .ifFailed {
          message =>
            log.log(message)
            System.exit(1)
        }
        .successful
    }
    log.log(s"Finished in ${toCompile.duration.toMillis}ms")

    val out = Timed {
      new TypespaceCompilerFSFacade(toCompile)
        .compile(itarget, option)
    }

    val allPaths = out.compilationProducts.paths

    log.log(s"${allPaths.size} source files from ${toCompile.size} domains produced in `$itarget` in ${out.duration.toMillis}ms")
    log.log(s"Archive: ${out.zippedOutput}")
    log.log("")
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

  private def toOption(env: Map[String, String])(lopt: LanguageOpts): UntypedCompilerOptions = {
    val lang = IDLLanguage.parse(lopt.id)
    val exts = getExt(lang, lopt.extensions)

    val patch = toJson(ConfigFactory.parseMap((env ++ lopt.overrides).asJava).root().unwrapped())
    val reader = new ManifestReader(patch, lang, lopt.manifest)
    val manifest = reader.read()

    UntypedCompilerOptions(lang, exts, manifest, lopt.withRuntime)
  }

  private def toJson(v: AnyRef): Json = {
    import io.circe.syntax._

    v match {
      case m: java.util.HashMap[_, _] =>
        m.asScala
          .map {
            case (k, value) =>
              k.toString -> toJson(value.asInstanceOf[AnyRef])
          }
          .asJson

      case s: String =>
        s.asJson

    }
  }


  private def getExt(lang: IDLLanguage, filter: List[String]): Seq[TranslatorExtension] = {
    val descriptor = TypespaceCompilerBaseFacade.descriptor(lang)
    val negative = filter.filter(_.startsWith("-")).map(_.substring(1)).map(ExtensionId).toSet
    descriptor.defaultExtensions.filterNot(e => negative.contains(e.id))
  }

}



