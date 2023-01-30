package izumi.distage.config.codec

import com.typesafe.config.ConfigException.Missing
import com.typesafe.config.ConfigValue
import izumi.distage.config.model.exceptions.DIConfigReadException
import izumi.reflect.Tag

import scala.util.{Failure, Success, Try}
import izumi.distage.config.DistageConfigImpl

/**
  * Config reader that uses a [[pureconfig.ConfigReader pureconfig.ConfigReader]] implicit instance for a type
  * to decode it from Typesafe Config.
  *
  * Always automatically derives a codec if it's not available.
  *
  * Automatic derivation will use **`camelCase`** fields, NOT `kebab-case` fields,
  * as in default pureconfig. It also overrides pureconfig's default `type` field
  * type discriminator for sealed traits, instead using a `circe`-like format with a single-key object.
  *
  * Example:
  *
  * {{{
  *   sealed trait AorB
  *   final case class A(a: Int) extends AorB
  *   final case class B(b: String) extends AorB
  *
  *   final case class Config(values: List[AorB])
  * }}}
  *
  * In config:
  *
  * {{{
  *   config {
  *     values = [
  *       { A { a = 123 } },
  *       { B { b = cba } }
  *     ]
  *   }
  * }}}
  *
  * Auto-derivation will work without importing `pureconfig.generic.auto._` and without any other imports
  *
  * You may use [[izumi.distage.config.codec.PureconfigAutoDerive]] f you want to use `DIConfigReader`'s deriving strategy to derive a standalone `pureconfig` codec:
  *
  * {{{
  *   final case class Abc(a: Duration, b: Regex, c: URL)
  *
  *   object Abc {
  *     implicit val configReader: pureconfig.ConfigReader[Abc] =
  *       PureconfigAutoDerive[Abc]
  *   }
  * }}}
  */
trait DIConfigReader[A] extends AbstractDIConfigReader[A] {
  protected def decodeConfigValue(configValue: ConfigValue): Try[A]

  final def decodeConfig(config: DistageConfigImpl): Try[A] = decodeConfigValue(config.root())

  final def decodeConfig(path: String)(config: DistageConfigImpl)(implicit tag: Tag[A]): A = {
    unpackResult(config, path)(decodeConfigValue(config.getValue(path)))
  }

  final def map[B](f: A => B): DIConfigReader[B] = decodeConfigValue(_).map(f)

  final def flatMap[B](f: A => DIConfigReader[B]): DIConfigReader[B] = cv => decodeConfigValue(cv).flatMap(f(_).decodeConfigValue(cv))

  final def decodeConfigWithDefault(path: String)(default: => A)(config: DistageConfigImpl)(implicit tag: Tag[A]): A = {
    try {
      val cv = config.getValue(path)
      unpackResult(config, path)(decodeConfigValue(cv))
    } catch {
      case _: Missing => default
    }
  }

  private[this] def unpackResult[T: Tag](config: DistageConfigImpl, path: String)(t: => Try[T]): T = {
    Try(t).flatten match {
      case Failure(exception) =>
        throw new DIConfigReadException(
          s"""Couldn't read configuration type at path="$path" as type `${Tag[T].tag}` due to error:
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

object DIConfigReader extends LowPriorityDIConfigReaderInstances2 {
  @inline def apply[T: DIConfigReader]: DIConfigReader[T] = implicitly

}
