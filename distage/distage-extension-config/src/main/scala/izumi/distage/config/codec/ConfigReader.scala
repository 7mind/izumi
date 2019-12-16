package izumi.distage.config.codec

import com.typesafe.config.{ConfigObject, ConfigValue, ConfigValueFactory}
import io.circe.Decoder
import io.circe.config.parser

import scala.jdk.CollectionConverters._
import scala.util.Try

/**
  * Config reader that uses Circe Json Decoder instance for a type
  * to decode Typesafe Config.
  *
  * Always automatically derives a codec if it's not available.
  *
  * Automatic derivation will also make additional Decoder instances
  * in [[CirceConfigInstances]] available. If you want to use this
  * deriving strategy for your own Decoder instance, you may use
  * `ConfigReader.deriveDecoder`:
  *
  * {{{
  *   case class Abc(a: Duration, b: Regex, c: URL) // types for which there is no default instance in Circe
  *   object Abc {
  *     implicit val decoder: Decoder[Abc] = ConfigReader.deriveDecoder
  *   }
  * }}}
  *
  * Or just import [[CirceConfigInstances]]:
  *
  * {{{
  *   object Abc {
  *     import CirceConfigInstances._
  *
  *     implicit val decoder: Decoder[Abc] = io.circe.derivation.deriveDecoder
  *   }
  * }}}
  */
trait ConfigReader[T] {
  def apply(configValue: ConfigValue): Try[T]
}

object ConfigReader extends LowPriorityConfigReaderInstances {
  def apply[T: ConfigReader]: ConfigReader[T] = implicitly

  def derive[T](implicit dec: CirceDerivationConfigStyle[T]): ConfigReader[T] = ConfigReader.deriveFromCirce(dec.value)

  implicit def deriveFromCirce[T](implicit dec: Decoder[T]): ConfigReader[T] = {
    cv =>
      val (wrappedValue, path) = cv match {
        case configObject: ConfigObject =>
          configObject.toConfig -> None
        case _ =>
          ConfigValueFactory.fromMap(Map("_root_" -> cv).asJava).toConfig -> Some("_root_")
      }
      Try {
        path.fold(parser.decode[T](wrappedValue)(dec)) {
          parser.decodePath[T](wrappedValue, _)(dec)
        }.toTry
      }.flatten
  }
}

trait LowPriorityConfigReaderInstances {
  implicit def materializeFromCirceDerivationWithCirceConfigInstances[T](implicit dec: CirceDerivationConfigStyle[T]): ConfigReader[T] = {
    ConfigReader.deriveFromCirce(dec.value)
  }
}
