package izumi.distage.config.codec

import io.circe.{ACursor, Json}
import izumi.distage.config.DistageConfigImpl
import izumi.distage.config.codec.DIConfigReaderPlatformSpecific.splitUnquotedConfigPath
import izumi.distage.config.model.exceptions.DIConfigReadException
import izumi.reflect.Tag

import scala.util.{Failure, Success, Try}

private[codec] trait DIConfigReaderPlatformSpecific[A] { self: DIConfigReader[A] =>

  override final def decodeConfig(config: DistageConfigImpl): Try[A] = {
    decodeConfigValue(Json.fromJsonObject(config))
  }

  override final def decodeConfig(path: String)(config: DistageConfigImpl)(implicit tag: Tag[A]): A = {
    decodeConfigWithDefault(path)(
      default = throw new DIConfigReadException(s"No configuration setting found for key '$path''", null)
    )(config)
  }

  override final def decodeConfigWithDefault(path: String)(default: => A)(config: DistageConfigImpl)(implicit tag: Tag[A]): A = {
    unpackResult(config, path) {
      val pathParts = splitUnquotedConfigPath(path)
      val cursor = pathParts.foldLeft(config.toJson.hcursor: ACursor)(_.downField(_))

      cursor.focus match {
        case None =>
          Try(default)
        case Some(json) =>
          decodeConfigValue(json)
      }
    }
  }

  private def unpackResult[T: Tag](config: DistageConfigImpl, path: String)(t: => Try[T]): T = {
    Try(t).flatten match {
      case Failure(exception) =>
        throw new DIConfigReadException(
          s"""Couldn't read configuration at path="$path" as type `${Tag[T].tag}` due to error:
             |
             |- ${exception.getMessage}
             |
             |Config was: ${Json.fromJsonObject(config).spaces2}
             |""".stripMargin,
          exception,
        )
      case Success(value) =>
        value
    }
  }
}

private[config] object DIConfigReaderPlatformSpecific {

  def splitUnquotedConfigPath(path: String): Array[String] = {
    if (path.contains('"')) {
      throw new IllegalArgumentException(
        "Quoted config paths are not supported in Scala.js version of distage-extension-config. Open a GitHub issue or PR if you need this."
      )
    }
    path.split('.')
  }

}
