package izumi.distage.config.codec

import izumi.distage.config.DistageConfigValueImpl
import izumi.distage.config.model.AppConfig
import izumi.reflect.Tag

import scala.util.Try

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
  * You may use [[izumi.distage.config.codec.PureconfigAutoDerive]] if you want to use `DIConfigReader`'s deriving strategy to derive a standalone `pureconfig` codec:
  *
  * {{{
  *   final case class Abc(a: Duration, b: Regex, c: URL)
  *
  *   object Abc {
  *     implicit val configReader: pureconfig.ConfigReader[Abc] =
  *       PureconfigAutoDerive[Abc]
  *   }
  * }}}
  *
  * @note on Scala.js DIConfigReader uses [[io.circe.Decoder]] instances to decode JSON configs, not HOCON as on JVM
  */
trait DIConfigReader[A] extends AbstractDIConfigReader[A] with DIConfigReaderPlatformSpecific[A] { self =>
  protected def decodeConfigValue(configValue: DistageConfigValueImpl): Try[A]

  final def map[B](f: A => B): DIConfigReader[B] = new DIConfigReader[B] {
    override protected def decodeConfigValue(configValue: DistageConfigValueImpl): Try[B] = self.decodeConfigValue(configValue).map(f)
  }

  final def flatMap[B](f: A => DIConfigReader[B]): DIConfigReader[B] = new DIConfigReader[B] {
    override protected def decodeConfigValue(configValue: DistageConfigValueImpl): Try[B] = {
      self.decodeConfigValue(configValue).flatMap(f(_).decodeConfigValue(configValue))
    }
  }

  final def decodeAppConfig(path: String)(implicit tag: Tag[A]): AppConfig => A = {
    appConfig => decodeConfig(path)(appConfig.config)
  }

  final def decodeAppConfigWithDefault(path: String)(default: => A)(implicit tag: Tag[A]): AppConfig => A = {
    appConfig => decodeConfigWithDefault(path)(default)(appConfig.config)
  }
}

object DIConfigReader extends DIConfigReaderDerivationPlatformSpecific {
  @inline def apply[T: DIConfigReader]: DIConfigReader[T] = implicitly
}
