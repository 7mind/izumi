package izumi.idealingua.compiler

import java.nio.file._
import java.time.{ZoneId, ZonedDateTime}

import izumi.fundamentals.platform.files.IzFiles
import izumi.fundamentals.platform.language.Quirks._
import izumi.fundamentals.platform.resources.{IzManifest, IzResources}
import izumi.fundamentals.platform.strings.IzString._
import izumi.fundamentals.platform.time.Timed
import izumi.idealingua.compiler.Codecs._
import izumi.idealingua.il.loader.{LocalModelLoaderContext, ModelResolver}
import izumi.idealingua.model.loader.UnresolvedDomains
import izumi.idealingua.model.publishing.{BuildManifest, ProjectVersion}
import izumi.idealingua.translator._
import com.typesafe.config.ConfigFactory
import io.circe
import io.circe.parser.parse
import io.circe.syntax._
import io.circe.{Json, JsonObject}

import scala.jdk.CollectionConverters._
import scala.util.Try

object CommandlineIDLCompiler {
  private val log: CompilerLog = CompilerLog.Default
  private val shutdown: Shutdown = ShutdownImpl

  def main(args: Array[String]): Unit = {
    val mf = IzManifest.manifest[CommandlineIDLCompiler.type]().map(IzManifest.read)
    val izumiVersion = mf.map(_.version.toString).getOrElse("UKNNOWN-IZUMI")
    val izumiInfoVersion = mf.map(_.justVersion).getOrElse("UNKNOWN-BUILD")

    log.log(s"Izumi IDL Compiler $izumiInfoVersion")

    val conf = parseArgs(args)


    val results = Seq(
      initDir(conf),
      runCompilations(izumiVersion, conf),
      runPublish(conf)
    )

    if (!results.contains(true)) {
      log.log("There was nothing to do. Try to run with `:help`")
    }
  }

  private def runPublish(conf: IDLCArgs): Boolean = {
    if (conf.publish && conf.languages.nonEmpty) {
      conf.languages.foreach { lang =>
        val manifest = toOption(conf, Map.empty)(lang).manifest
        publishLangArtifacts(conf, lang, manifest) match {
          case Left(err) => throw err
          case _ => ()
        }
      }
      true
    } else
      false
  }

  def publishLangArtifacts(conf: IDLCArgs, langOpts: LanguageOpts, manifest: BuildManifest): Either[Throwable, Unit] = for {
    credsFile <- Either.cond(langOpts.credentials.isDefined, langOpts.credentials.get,
      new IllegalArgumentException(s"Can't publish ${langOpts.id} with empty credentials file. " +
        s"Use `--credentials` command line arg to set it"
      )
    )
    lang <- Try(IDLLanguage.parse(langOpts.id)).toEither
    creds <- new CredentialsReader(lang, credsFile).read(toJson(langOpts.overrides))
    target <- Try(conf.target.toAbsolutePath.resolve(langOpts.id)).toEither
    res <- new ArtifactPublisher(target, lang, creds, manifest).publish()
  } yield res

  private def initDir(conf: IDLCArgs): Boolean = {
    conf.init match {
      case Some(p) =>
        log.log(s"Initializing layout in `$p` ...")
        val f = p.toFile
        if (f.exists()) {
          if (f.isDirectory) {
            if (f.listFiles().nonEmpty) {
              shutdown.shutdown(s"Exists and not empty: $p")
            }
          } else {
            shutdown.shutdown(s"Exists and not a directory: $p")
          }
        }

        val mfdir = p.resolve("manifests")
        mfdir.toFile.mkdirs().discard()
        IzResources.copyFromClasspath("defs/example", p).discard()

        TypespaceCompilerBaseFacade.descriptors.foreach {
          d =>
            Files.write(mfdir.resolve(s"${d.language.toString.toLowerCase}.json"), new ManifestWriter().write(d.defaultManifest).utf8).discard()
        }

        Files.write(p.resolve(s"version.json"), VersionOverlay.example.asJson.toString().utf8).discard()
        true
      case None =>
        false
    }
  }

  private def runCompilations(izumiVersion: String, conf: IDLCArgs) = {
    if (conf.languages.nonEmpty) {
      log.log("Reading manifests...")
      val toRun = conf.languages.map(toOption(conf, Map("common.izumiVersion" -> izumiVersion)))
      log.log("Going to compile:")
      log.log(toRun.niceList())
      log.log("")

      val path = conf.source.toAbsolutePath
      val target = conf.target.toAbsolutePath
      target.toFile.mkdirs()

      log.log(s"Loading definitions from `$path`...")

      val loaded = Timed {
        if (path.toFile.exists() && path.toFile.isDirectory) {
          val context = new LocalModelLoaderContext(Seq(path, conf.overlay.toAbsolutePath), Seq.empty)
          context.loader.load()
        } else {
          shutdown.shutdown(s"Not exists or not a directory: $path")
        }
      }
      log.log(s"Done: ${loaded.value.domains.results.size} in ${loaded.duration.toMillis}ms")
      log.log("")

      toRun.foreach {
        option =>
          runCompiler(target, loaded, option)
      }
      true
    } else {
      false
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
            shutdown.shutdown(message)
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

  private def parseArgs(args: Array[String]): IDLCArgs = {
    IDLCArgs.parseUnsafe(args)
  }

  private def toOption(conf: IDLCArgs, env: Map[String, String])(lopt: LanguageOpts): UntypedCompilerOptions = {
    val lang = IDLLanguage.parse(lopt.id)
    val exts = getExt(lang, lopt.extensions)

    val manifest = readManifest(conf, env, lopt, lang)
    UntypedCompilerOptions(lang, exts, manifest, lopt.withRuntime)
  }

  private def readManifest(conf: IDLCArgs, env: Map[String, String], lopt: LanguageOpts, lang: IDLLanguage): BuildManifest = {
    val default = Paths.get("version.json")

    val overlay = conf.versionOverlay.map(loadVersionOverlay(lang)) match {
      case Some(value) =>
        Some(value)
      case None if default.toFile.exists() =>
        log.log(s"Found $default, using as version overlay for $lang...")
        Some(loadVersionOverlay(lang)(default))
      case None =>
        None
    }

    val overlayJson = overlay match {
      case Some(Right(value)) =>
        value
      case Some(Left(e)) =>
        shutdown.shutdown(s"Failed to parse version overlay: ${e.getMessage}")
      case None =>
        JsonObject.empty.asJson
    }

    val envJson = toJson(env)
    val languageOverridesJson = toJson(lopt.overrides)
    val globalOverridesJson = toJson(conf.overrides)
    val patch = overlayJson.deepMerge(globalOverridesJson).deepMerge(envJson).deepMerge(languageOverridesJson)

    val reader = new ManifestReader(log, shutdown, patch, lang, lopt.manifest)
    val manifest = reader.read()
    manifest
  }

  private def loadVersionOverlay(lang: IDLLanguage)(path: Path): Either[circe.Error, Json] = {
    import io.circe.literal._

    for {
      parsed <- parse(IzFiles.readString(path.toFile))
      decoded <- parsed.as[VersionOverlay]
    } yield {
      val defQualifier = decoded.snapshotQualifiers.getOrElse(lang.toString.toLowerCase, "UNSET")
      val timestamp = ZonedDateTime.now(ZoneId.of("UTC")).toEpochSecond
      val qualifier = if (lang == IDLLanguage.Typescript) s"$defQualifier-$timestamp" else defQualifier
      val version = ProjectVersion(decoded.version, decoded.release, qualifier)
      json"""{"common": {"version": $version}}"""
    }
  }

  private def toJson(env: Map[String, String]) = {
    valToJson(ConfigFactory.parseMap(env.asJava).root().unwrapped())
  }

  private def valToJson(v: AnyRef): Json = {
    import io.circe.syntax._

    v match {
      case m: java.util.HashMap[_, _] =>
        m.asScala
          .map {
            case (k, value) =>
              k.toString -> valToJson(value.asInstanceOf[AnyRef])
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

case class VersionOverlay(version: String, release: Boolean, snapshotQualifiers: Map[String, String])

object VersionOverlay {
  def example: VersionOverlay = {
    VersionOverlay(
      "0.0.1",
      release = false,
      Map(
        IDLLanguage.Scala -> "SNAPSHOT",
        IDLLanguage.Typescript -> "build.0",
        IDLLanguage.Go -> "0",
        IDLLanguage.CSharp -> "alpha",
      )
        .map { case (k, v) => k.toString.toLowerCase -> v }
    )
  }
}
