package izumi.distage.config

import izumi.distage.config.ConfigModuleDef.FromConfig
import izumi.distage.config.codec.DIConfigReader
import izumi.distage.model.definition.BindingTag.ConfTag
import izumi.distage.model.definition.ModuleDef
import izumi.distage.model.definition.dsl.ModuleDefDSL.{MakeDSL, MakeDSLNamedAfterFrom, MakeDSLUnnamedAfterFrom}
import izumi.distage.model.providers.ProviderMagnet
import izumi.fundamentals.platform.language.CodePositionMaterializer
import izumi.fundamentals.platform.language.Quirks._
import izumi.reflect.Tag

import scala.language.implicitConversions

trait ConfigModuleDef extends ModuleDef {
  final def makeConfig[T: Tag: DIConfigReader](path: String)(implicit pos: CodePositionMaterializer): MakeDSLUnnamedAfterFrom[T] = {
    pos.discard()
    make[T].tagged(ConfTag(path)).from(wireConfig[T](path))
  }
  final def makeConfigNamed[T: Tag: DIConfigReader](path: String)(implicit pos: CodePositionMaterializer): MakeDSLNamedAfterFrom[T] = {
    pos.discard()
    make[T].named(path).tagged(ConfTag(path)).from(wireConfig[T](path))
  }
  final def makeConfigWithDefault[T: Tag: DIConfigReader](path: String)(default: => T)(implicit pos: CodePositionMaterializer): MakeDSLUnnamedAfterFrom[T] = {
    pos.discard()
    make[T].tagged(ConfTag(path)).from(wireConfigWithDefault[T](path)(default))
  }

  @inline final def wireConfig[T: Tag: DIConfigReader](path: String): ProviderMagnet[T] = {
    ConfigModuleDef.wireConfig[T](path)
  }
  @inline final def wireConfigWithDefault[T: Tag: DIConfigReader](path: String)(default: => T): ProviderMagnet[T] = {
    ConfigModuleDef.wireConfigWithDefault[T](path)(default)
  }

  @inline implicit final def FromConfig[T](dsl: MakeDSL[T]): FromConfig[T] = new FromConfig[T](dsl)
}

object ConfigModuleDef {
  final class FromConfig[T](private val make: MakeDSL[T]) extends AnyVal {
    def fromConfig(path: String)(implicit tag: Tag[T], dec: DIConfigReader[T]): MakeDSLUnnamedAfterFrom[T] = {
      make.tagged(ConfTag(path)).from(wireConfig[T](path))
    }
    def fromConfigNamed(path: String)(implicit tag: Tag[T], dec: DIConfigReader[T]): MakeDSLNamedAfterFrom[T] = {
      make.named(path).tagged(ConfTag(path)).from(wireConfig[T](path))
    }
  }

  def wireConfig[T: Tag: DIConfigReader](path: String): ProviderMagnet[T] = {
    DIConfigReader[T].decodeAppConfig(path)
  }
  def wireConfigWithDefault[T: Tag: DIConfigReader](path: String)(default: => T): ProviderMagnet[T] = {
    DIConfigReader[T].decodeAppConfigWithDefault(path)(default)
  }
}
