package izumi.distage.config.codec

import com.typesafe.config.ConfigException.Missing
import izumi.distage.config.DistageConfigImpl
import izumi.distage.config.model.exceptions.DIConfigReadException
import izumi.reflect.Tag

import scala.util.{Failure, Success, Try}

private[codec] trait DIConfigReaderPlatformSpecific[A] { self: DIConfigReader[A] =>

  override final def decodeConfig(config: DistageConfigImpl): Try[A] = {
    decodeConfigValue(config.root())
  }

  override final def decodeConfig(path: String)(config: DistageConfigImpl)(implicit tag: Tag[A]): A = {
    unpackResult(config, path)(decodeConfigValue(config.getValue(path)))
  }

  override final def decodeConfigWithDefault(path: String)(default: => A)(config: DistageConfigImpl)(implicit tag: Tag[A]): A = {
    try {
      val cv = config.getValue(path)
      unpackResult(config, path)(decodeConfigValue(cv))
    } catch {
      case _: Missing => default
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
             |Config was: ${config.origin().description()}
             |""".stripMargin,
          exception,
        )
      case Success(value) =>
        value
    }
  }

}
