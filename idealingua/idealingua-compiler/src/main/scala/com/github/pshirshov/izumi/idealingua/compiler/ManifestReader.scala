package com.github.pshirshov.izumi.idealingua.compiler

import java.io.File
import java.nio.file.Paths

import com.github.pshirshov.izumi.fundamentals.platform.files.IzFiles
import com.github.pshirshov.izumi.idealingua.model.publishing.BuildManifest
import com.github.pshirshov.izumi.idealingua.model.publishing.manifests.{CSharpBuildManifest, GoLangBuildManifest, ScalaBuildManifest, TypeScriptBuildManifest}
import com.github.pshirshov.izumi.idealingua.translator.IDLLanguage
import io.circe.parser.parse
import io.circe.syntax._
import io.circe.{Decoder, Encoder, Json}

import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

class ManifestReader(patch: Json, lang: IDLLanguage, file: Option[File]) extends Codecs {
  private val log = CompilerLog.Default

  def read(): BuildManifest = {
    lang match {
      case IDLLanguage.Scala =>
        readManifest(ScalaBuildManifest.default)
      case IDLLanguage.Typescript =>
        readManifest(TypeScriptBuildManifest.default)
      case IDLLanguage.Go =>
        readManifest(GoLangBuildManifest.default)
      case IDLLanguage.CSharp =>
        readManifest(CSharpBuildManifest.default)
    }
  }

  private def readManifest[T <: BuildManifest : ClassTag : Decoder : Encoder](default: T): T = {
    val defaultJson = default.asJson.deepMerge(patch)

    val mf = file match {
      case Some(path) if path.toString == "@" =>
        RawMf.Default(defaultJson, suppressed = true)

      case Some(path) if path.toString == "+" =>
        readMfFromFile(Paths.get("manifests", s"${lang.toString.toLowerCase}.json").toFile)

      case Some(path) =>
        readMfFromFile(path)

      case None =>
        RawMf.Default(defaultJson, suppressed = false)
    }

    val rawMf = mf match {
      case RawMf.Loaded(json) =>
        json

      case RawMf.Default(json, suppressed) =>
        if (!suppressed) {
          log.log(s"No manifest defined for $lang, using default (you may suppress this message by using `-m @` switch):")
          log.log(json.toString())
          log.log("")
        }
        json

      case RawMf.FailedToLoad(e, path) =>
        log.log(s"Failed to parse manifest for $lang from $path, check if it corresponds to the following example:")
        log.log(defaultJson.toString())
        log.log("")
        shutdown(s"Failed to parse $lang manifest from $path: ${e.toString}")

      case RawMf.Failed(e, path) =>
        log.log(s"Failed to load manifest for $lang from $path, you may use the following example to create it:")
        log.log(defaultJson.toString())
        log.log("")
        shutdown(s"Failed to load $lang manifest from $path: ${e.toString}")
    }

    rawMf.deepMerge(patch).as[T] match {
      case Left(value) =>
        shutdown(s"Have $lang manifest but it failed to parse: ${value.toString}")

      case Right(value) =>
        value
    }
  }


  private def shutdown(message: String): Nothing = {
    log.log(message)
    System.out.flush()
    System.exit(1)
    throw new IllegalArgumentException(message)
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

  final case class Default(json: Json, suppressed: Boolean) extends RawMf

  final case class FailedToLoad(e: Throwable, path: File) extends RawMf

  final case class Failed(e: Throwable, path: File) extends RawMf

}
