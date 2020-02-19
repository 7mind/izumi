package izumi.distage.config

import izumi.distage.config.ConfigModuleDef.FromConfig
import izumi.distage.config.codec.DIConfigReader
import izumi.distage.model.definition.BindingTag.ConfTag
import izumi.distage.model.definition.ModuleDef
import izumi.distage.model.definition.dsl.ModuleDefDSL.{MakeDSL, MakeDSLNamedAfterFrom, MakeDSLUnnamedAfterFrom}
import izumi.fundamentals.platform.language.CodePositionMaterializer
import izumi.fundamentals.platform.language.Quirks._
import izumi.fundamentals.reflection.Tags.Tag

import scala.language.implicitConversions

trait ConfigModuleDef extends ModuleDef {
  final def makeConfig[T: Tag: DIConfigReader](path: String)(implicit pos: CodePositionMaterializer): MakeDSLUnnamedAfterFrom[T] = {
    pos.discard()
    make[T].tagged(ConfTag(path)).from(DIConfigReader[T].decodeAppConfig(path))
  }
  final def makeConfigNamed[T: Tag: DIConfigReader](path: String)(implicit pos: CodePositionMaterializer): MakeDSLNamedAfterFrom[T] = {
    pos.discard()
    make[T].named(path).tagged(ConfTag(path)).from(DIConfigReader[T].decodeAppConfig(path))
  }

  @inline implicit final def FromConfig[T](dsl: MakeDSL[T]): FromConfig[T] = new FromConfig[T](dsl)
}

object ConfigModuleDef {
  final class FromConfig[T](private val dsl: MakeDSL[T]) extends AnyVal {
    def fromConfig(path: String)(implicit tag: Tag[T], dec: DIConfigReader[T]): MakeDSLUnnamedAfterFrom[T] = {
      dsl.tagged(ConfTag(path)).from(dec.decodeAppConfig(path))
    }
    def fromConfigNamed(path: String)(implicit tag: Tag[T], dec: DIConfigReader[T]): MakeDSLNamedAfterFrom[T] = {
      dsl.named(path).tagged(ConfTag(path)).from(dec.decodeAppConfig(path))
    }
  }
}
