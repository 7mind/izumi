package izumi.distage.config.codec

import com.typesafe.config.ConfigException.Missing
import com.typesafe.config.{Config, ConfigValue}
import izumi.distage.config.model.AppConfig
import izumi.distage.config.model.exceptions.DIConfigReadException
import izumi.fundamentals.platform.exceptions.IzThrowable._
import izumi.reflect.Tag
import pureconfig.ConfigReader
import pureconfig.error.ConfigReaderException

import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

/**
  * Config reader that uses pureconfig.ConfigReader instance for a type
  * to decode Typesafe Config.
  *
  * Always automatically derives a codec if it's not available.
  *
  * Automatic derivation will use **`camelCase`** fields, not `kebab-case` fields,
  * as in default pureconfig. It also override pureconfig's default `type` field
  * type discriminator for sealed traits. Instead, using `circe`-like format with a single-key object. Example:
  *
  * {{{
  *   sealed trait AorB
  *   final case class A(a: Int) extends AorB
  *   final case class B(b: String) extends AorB
  *
  *   final case class Config(values: List[AorB])
  * }}}
  *
  * in config:
  *
  * {{{
  *   config {
  *     values = [{ A { a = 5 } }, { B { b = cba } }]
  *   }
  * }}}
  *
  * It will also work without importing `pureconfig.generic.auto._`
  *
  * If you want to use it to recursively derive a pureconfig codec,
  * you may use `PureconfigAutoDerive[T]`:
  *
  * {{{
  *   final case class Abc(a: Duration, b: Regex, c: URL)
  *   object Abc {
  *     implicit val configReader: pureconfig.ConfigReader[Abc] = PureconfigAutoDerive[Abc]
  *   }
  * }}}
  */
trait DIConfigReader[A] {
  def decodeConfigValue(configValue: ConfigValue): Try[A]

  final def map[B](f: A => B): DIConfigReader[B] = decodeConfigValue(_).map(f)
  final def flatMap[B](f: A => DIConfigReader[B]): DIConfigReader[B] = cv => decodeConfigValue(cv).flatMap(f(_).decodeConfigValue(cv))

  final def decodeAppConfig(path: String)(implicit tag: Tag[A]): AppConfig => A = {
    appConfig => decodeConfig(path)(appConfig.config)
  }

  final def decodeConfig(path: String)(config: Config)(implicit tag: Tag[A]): A = {
    unpackResult(config, path)(decodeConfigValue(config.getValue(path)))
  }

  final def decodeAppConfigWithDefault(path: String)(default: => A)(implicit tag: Tag[A]): AppConfig => A = {
    appConfig => decodeConfigWithDefault(path)(default)(appConfig.config)
  }

  final def decodeConfigWithDefault(path: String)(default: => A)(config: Config)(implicit tag: Tag[A]): A = {
    try {
      val cv = config.getValue(path)
      unpackResult(config, path)(decodeConfigValue(cv))
    } catch {
      case _: Missing => default
    }
  }

  private[this] def unpackResult[T: Tag](config: Config, path: String)(t: => Try[T]): T = {
    Try(t).flatten match {
      case Failure(exception) =>
        throw new DIConfigReadException(
          s"""Couldn't read configuration type at path="$path" as type `${Tag[T].tag}` due to error:
             |
             |  ${exception.stackTrace}
             |
             |Config was: $config
             |""".stripMargin,
          exception,
        )
      case Success(value) =>
        value
    }
  }
}

object DIConfigReader extends LowPriorityDIConfigReaderInstances {
  @inline def apply[T: DIConfigReader]: DIConfigReader[T] = implicitly

  def derived[T: ClassTag](implicit dec: PureconfigAutoDerive[T]): DIConfigReader[T] =
    DIConfigReader.deriveFromPureconfigConfigReader[T](implicitly, dec.value)

  implicit def deriveFromPureconfigConfigReader[T: ClassTag](implicit dec: ConfigReader[T]): DIConfigReader[T] = {
    cv =>
      dec.from(cv) match {
        case Left(errs) => Failure(ConfigReaderException[T](errs))
        case Right(value) => Success(value)
      }
  }
}

sealed trait LowPriorityDIConfigReaderInstances {
  implicit final def deriveFromPureconfigAutoDerive[T: ClassTag](implicit dec: PureconfigAutoDerive[T]): DIConfigReader[T] = {
    DIConfigReader.deriveFromPureconfigConfigReader(implicitly, dec.value)
  }
}
