package izumi.distage.config.codec

import izumi.distage.constructors.{ClassConstructor, FactoryConstructor}
import izumi.reflect.Tag
import pureconfig.{ConfigReader, Exported}

import scala.deriving.Mirror
import scala.language.implicitConversions

final class MetaAutoDerive[A](val value: DIConfigMeta[A]) extends AnyVal

object MetaAutoDerive {
  @inline def apply[A](implicit ev: MetaAutoDerive[A]): DIConfigMeta[A] = ev.value

  @inline def derived[A](implicit ev: MetaAutoDerive[A]): DIConfigMeta[A] = ev.value

  inline implicit def materialize[A](implicit m: Mirror.Of[A]): MetaAutoDerive[A] = {
    import izumi.distage.config.codec.MetaInstances.auto.exportDerivedDIConfigMeta
    import izumi.distage.config.codec.MetaInstances.given

    new MetaAutoDerive[A](izumi.distage.config.codec.MetaInstances.configReaderDerivation.derived[A](using m))
  }
}
