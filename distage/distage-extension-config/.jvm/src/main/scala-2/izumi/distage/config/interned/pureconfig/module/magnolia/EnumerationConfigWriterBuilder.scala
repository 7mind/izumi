package izumi.distage.config.interned.pureconfig.module.magnolia

import com.typesafe.config.{ConfigValue, ConfigValueFactory}
import magnolia1.*
import pureconfig.ConfigWriter

import scala.language.experimental.macros

/** A type class to build `ConfigWriter`s for sealed families of case objects where each type is encoded as a
  * `ConfigString` based on the type name.
  *
  * @tparam A
  *   the type of objects capable of being written as an enumeration
  */
private[magnolia] trait EnumerationConfigWriterBuilder[A] {
  def build(transformName: String => String): ConfigWriter[A]
}

object EnumerationConfigWriterBuilder {
  type Typeclass[A] = EnumerationConfigWriterBuilder[A]

  def join[A](ctx: CaseClass[EnumerationConfigWriterBuilder, A]): EnumerationConfigWriterBuilder[A] =
    new EnumerationConfigWriterBuilder[A] {
      def build(transformName: String => String): ConfigWriter[A] =
        new ConfigWriter[A] {
          def to(a: A): ConfigValue = ConfigValueFactory.fromAnyRef(transformName(ctx.typeName.short))
        }
    }

  def split[A](ctx: SealedTrait[EnumerationConfigWriterBuilder, A]): EnumerationConfigWriterBuilder[A] =
    new EnumerationConfigWriterBuilder[A] {
      def build(transformName: String => String): ConfigWriter[A] =
        new ConfigWriter[A] {
          def to(a: A): ConfigValue =
            ctx.split(a)(subtype => subtype.typeclass.build(transformName).to(subtype.cast(a)))
        }
    }

  implicit def `export`[A]: EnumerationConfigWriterBuilder[A] = macro Magnolia.gen[A]
}
