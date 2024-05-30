package izumi.distage.config.codec

import izumi.distage.constructors.{ClassConstructor, FactoryConstructor}
import izumi.reflect.Tag
import pureconfig.{ConfigReader, Exported}

import scala.deriving.Mirror
import scala.language.implicitConversions

/**
  * Derive `pureconfig.ConfigReader` for A and for its fields recursively with `pureconfig-magnolia`
  *
  * This differs from just using [[pureconfig.module.magnolia.auto.reader.exportReader]] by using
  * different configuration, defined in [[PureconfigInstances]], specifically:
  *
  * 1. Field name remapping is disabled, `camelCase` fields will remain camelCase, not `kebab-case`
  * 2. Sealed traits are rendered as in `circe`, using a wrapper object with a single field, instead of using a `type` field. Example:
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
  *     values = [
  *       { A { a = 123 } },
  *       { B { b = cba } }
  *     ]
  *   }
  * }}}
  */
final class PureconfigAutoDerive[A](val value: ConfigReader[A]) extends AnyVal

object PureconfigAutoDerive {
  @inline def apply[A](implicit ev: PureconfigAutoDerive[A]): ConfigReader[A] = ev.value

  @inline def derived[A](implicit ev: PureconfigAutoDerive[A]): ConfigReader[A] = ev.value

  inline implicit def materialize[A](implicit m: Mirror.Of[A]): PureconfigAutoDerive[A] = {
    import izumi.distage.config.codec.PureconfigInstances.auto.exportDerivedConfigReader
    import izumi.distage.config.codec.PureconfigInstances.given
    
    new PureconfigAutoDerive[A](izumi.distage.config.codec.PureconfigInstances.configReaderDerivation.derived[A](using m))
  }
}
