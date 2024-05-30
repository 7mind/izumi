package izumi.distage.config

import izumi.distage.config.ConfigModuleDef.FromConfig
import izumi.distage.config.model.{AppConfig, ConfTag}
import izumi.distage.model.definition.ModuleDef
import izumi.distage.model.definition.dsl.ModuleDefDSL.{MakeDSL, MakeDSLNamedAfterFrom, MakeDSLUnnamedAfterFrom}
import izumi.distage.model.providers.Functoid
import izumi.fundamentals.platform.language.CodePositionMaterializer
import izumi.fundamentals.platform.language.Quirks.*
import izumi.reflect.Tag
import izumi.distage.config.codec.{DIConfigMeta, DIConfigReader}

import scala.language.implicitConversions

trait ConfigModuleDef extends ModuleDef {
  final def makeConfig[T: Tag: DIConfigReader: DIConfigMeta](path: String)(implicit pos: CodePositionMaterializer): MakeDSLUnnamedAfterFrom[T] = {
    pos.discard() // usage in `make[T]` not detected
    make[T].fromConfig(path)
  }
  final def makeConfigNamed[T: Tag: DIConfigReader: DIConfigMeta](path: String)(implicit pos: CodePositionMaterializer): MakeDSLNamedAfterFrom[T] = {
    pos.discard()
    make[T].fromConfigNamed(path)
  }
  final def makeConfigWithDefault[T: Tag: DIConfigReader: DIConfigMeta](
    path: String
  )(default: => T
  )(implicit pos: CodePositionMaterializer
  ): MakeDSLUnnamedAfterFrom[T] = {
    pos.discard()
    make[T].fromConfigWithDefault(path)(default)
  }

  @inline final def wireConfig[T: Tag: DIConfigReader](path: String): Functoid[T] = {
    ConfigModuleDef.wireConfig[T](path)
  }
  @inline final def wireConfigWithDefault[T: Tag: DIConfigReader: DIConfigMeta](path: String)(default: => T): Functoid[T] = {
    ConfigModuleDef.wireConfigWithDefault[T](path)(default)
  }

  @inline implicit final def FromConfig[T](dsl: MakeDSL[T]): FromConfig[T] = new FromConfig[T](dsl)
}

object ConfigModuleDef {
  final class FromConfig[T](private val make: MakeDSL[T]) extends AnyVal {
    def fromConfig(path: String)(implicit tag: Tag[T], dec: DIConfigReader[T], meta: DIConfigMeta[T]): MakeDSLUnnamedAfterFrom[T] = {
      val parser = wireConfig[T](path)
      make.tagged(ConfTag(path)(parser, meta.tpe)).from(parser)
    }
    def fromConfigNamed(path: String)(implicit tag: Tag[T], dec: DIConfigReader[T], meta: DIConfigMeta[T]): MakeDSLNamedAfterFrom[T] = {
      val parser = wireConfig[T](path)
      make.named(path).tagged(ConfTag(path)(parser, meta.tpe)).from(parser)
    }
    def fromConfigWithDefault(path: String)(default: => T)(implicit tag: Tag[T], dec: DIConfigReader[T], meta: DIConfigMeta[T]): MakeDSLUnnamedAfterFrom[T] = {
      val parser = wireConfigWithDefault[T](path)(default)
      make.tagged(ConfTag(path)(parser, meta.tpe)).from(parser)
    }
  }

  def wireConfig[K: Tag: DIConfigReader](path: String): AppConfig => K = {
    DIConfigReader[K].decodeAppConfig(path)
  }
  def wireConfigWithDefault[T: Tag: DIConfigReader](path: String)(default: => T): AppConfig => T = {
    DIConfigReader[T].decodeAppConfigWithDefault(path)(default)
  }
}
