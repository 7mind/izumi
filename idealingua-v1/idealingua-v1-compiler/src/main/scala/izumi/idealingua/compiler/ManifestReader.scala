package izumi.idealingua.compiler

import java.io.File
import java.nio.file.Paths

import izumi.fundamentals.platform.files.IzFiles
import izumi.idealingua.model.publishing.BuildManifest
import izumi.idealingua.model.publishing.manifests.{CSharpBuildManifest, GoLangBuildManifest, ScalaBuildManifest, TypeScriptBuildManifest}
import izumi.idealingua.translator.IDLLanguage
import io.circe.parser.parse
import io.circe.syntax._
import io.circe.{Decoder, Encoder, Json}

import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

import Codecs._

class ManifestWriter() {
  def write(mf: BuildManifest): String = {
    (mf match {
      case m: ScalaBuildManifest =>
        m.asJson
      case m: TypeScriptBuildManifest =>
        m.asJson
      case m: GoLangBuildManifest =>
        m.asJson
      case m: CSharpBuildManifest =>
        m.asJson
    }).toString()
  }
}

class ManifestReader(log: CompilerLog, shutdown: Shutdown, patch: Json, lang: IDLLanguage, file: Option[File]) {
  def read(): BuildManifest = {
    lang match {
      case IDLLanguage.Scala =>
        readManifest(ScalaBuildManifest.example)
      case IDLLanguage.Typescript =>
        readManifest(TypeScriptBuildManifest.example)
      case IDLLanguage.Go =>
        readManifest(GoLangBuildManifest.example)
      case IDLLanguage.CSharp =>
        readManifest(CSharpBuildManifest.example)
    }
  }

  private def readManifest[T <: BuildManifest : ClassTag : Decoder : Encoder](default: T): T = {
    val defaultJson = default.asJson.deepMerge(patch)
    val defaultMfFile = Paths.get("manifests", s"${lang.toString.toLowerCase}.json").toFile

    val mf = file match {
      case Some(path) if path.toString == "@" =>
        RawMf.Default(defaultJson, defaultMfFile, suppressed = true)

      case Some(path) if path.toString == "+" =>
        readMfFromFile(defaultMfFile)

      case Some(path) =>
        readMfFromFile(path)

      case None if defaultMfFile.exists() =>
        log.log(s"Will use default manifest ${defaultMfFile.toPath} for $lang")
        readMfFromFile(defaultMfFile)

      case None =>
        RawMf.Default(defaultJson, defaultMfFile, suppressed = false)
    }

    val rawMf = mf match {
      case RawMf.Loaded(json) =>
        json

      case RawMf.Default(json, d, suppressed) =>
        if (!suppressed) {
          log.log(s"No manifest defined for $lang and default manifest file `${d.toPath}` is missing, using default (you may suppress this message by using `-m @` switch):")
          log.log(json.toString())
          log.log("")
        }
        json

      case RawMf.FailedToLoad(e, path) =>
        log.log(s"Failed to parse manifest for $lang from $path, check if it corresponds to the following example:")
        log.log(defaultJson.toString())
        log.log("")
        shutdown.shutdown(s"Failed to parse $lang manifest from $path: ${e.toString}")

      case RawMf.Failed(e, path) =>
        log.log(s"Failed to load manifest for $lang from $path, you may use the following example to create it:")
        log.log(defaultJson.toString())
        log.log("")
        shutdown.shutdown(s"Failed to load $lang manifest from $path: ${e.toString}")
    }

    rawMf.deepMerge(patch).as[T] match {
      case Left(value) =>
        shutdown.shutdown(s"Have $lang manifest but it failed to parse: ${value.toString}")

      case Right(value) =>
        value
    }
  }


  private def readMfFromFile(path: File): RawMf = {
    Try(parse(IzFiles.readString(path))) match {
      case Success(Right(r)) =>
        RawMf.Loaded(r)
      case Success(Left(l)) =>
        RawMf.FailedToLoad(l, path)
      case Failure(f) =>
        RawMf.Failed(f, path)
    }
  }
}

sealed trait RawMf

object RawMf {

  final case class Loaded(json: Json) extends RawMf

  final case class Default(json: Json, defaultFile: File, suppressed: Boolean) extends RawMf

  final case class FailedToLoad(e: Throwable, path: File) extends RawMf

  final case class Failed(e: Throwable, path: File) extends RawMf

}
