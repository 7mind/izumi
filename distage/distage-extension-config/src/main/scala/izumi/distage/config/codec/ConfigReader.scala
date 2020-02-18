package izumi.distage.config.codec

import com.typesafe.config.ConfigValue
import pureconfig.error.ConfigReaderException

import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

/**
  * Config reader that uses pureconfig.ConfigReader instance for a type
  * to decode Typesafe Config.
  *
  * Always automatically derives a codec if it's not available.
  *
  * Automatic derivation will use **`camelCase`** fields, not `snake-case` fields,
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
trait ConfigReader[A] {
  def apply(configValue: ConfigValue): Try[A]

  final def map[B](f: A => B): ConfigReader[B] = apply(_).map(f)
  final def flatMap[B](f: A => ConfigReader[B]): ConfigReader[B] = cv => apply(cv).flatMap(f(_)(cv))
}

object ConfigReader extends LowPriorityConfigReaderInstances {
  @inline def apply[T: ConfigReader]: ConfigReader[T] = implicitly

  def derive[T: ClassTag](implicit dec: PureconfigAutoDerive[T]): ConfigReader[T] = {
    ConfigReader.deriveFromPureconfig[T](implicitly, dec.value)
  }

  implicit def deriveFromPureconfig[T: ClassTag](implicit dec: pureconfig.ConfigReader[T]): ConfigReader[T] = {
    cv =>
      dec.from(cv) match {
        case Left(errs) => Failure(ConfigReaderException[T](errs))
        case Right(value) => Success(value)
      }
  }
}

trait LowPriorityConfigReaderInstances {
  implicit def materializeFromPureconfigAutoDerive[T: ClassTag](implicit dec: PureconfigAutoDerive[T]): ConfigReader[T] = {
    ConfigReader.deriveFromPureconfig(implicitly, dec.value)
  }
}
