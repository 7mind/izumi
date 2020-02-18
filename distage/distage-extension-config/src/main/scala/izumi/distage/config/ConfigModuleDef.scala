package izumi.distage.config

import izumi.distage.config.codec.ConfigReader
import izumi.distage.config.model.AppConfig
import izumi.distage.config.model.exceptions.DIConfigReadException
import izumi.distage.model.definition.BindingTag.ConfTag
import izumi.distage.model.definition.ModuleDef
import izumi.distage.model.definition.dsl.ModuleDefDSL.{MakeDSL, MakeDSLNamedAfterFrom, MakeDSLUnnamedAfterFrom}
import izumi.fundamentals.platform.language.CodePositionMaterializer
import izumi.fundamentals.platform.language.Quirks._
import izumi.fundamentals.reflection.Tags.Tag

import scala.util.{Failure, Success, Try}

trait ConfigModuleDef extends ModuleDef {
  final def makeConfig[T: Tag: ConfigReader](path: String)(implicit pos: CodePositionMaterializer): MakeDSLUnnamedAfterFrom[T] = {
    pos.discard()
    make[T].tagged(ConfTag(path)).from(decodeConfig[T](path))
  }
  final def makeConfigNamed[T: Tag: ConfigReader](path: String)(implicit pos: CodePositionMaterializer): MakeDSLNamedAfterFrom[T] = {
    pos.discard()
    make[T].named(path).tagged(ConfTag(path)).from(decodeConfig[T](path))
  }

  final def decodeConfig[T: Tag: ConfigReader](path: String): AppConfig => T = {
    config: AppConfig =>
      unpackResult(config, path)(ConfigReader[T].apply(config.config.getValue(path)))
  }

  implicit final class FromConfig[T](private val dsl: MakeDSL[T]) {
    def fromConfig(path: String)(implicit tag: Tag[T], dec: ConfigReader[T]): MakeDSLUnnamedAfterFrom[T] = {
      dsl.tagged(ConfTag(path)).from(decodeConfig[T](path))
    }
    def fromConfigNamed(path: String)(implicit tag: Tag[T], dec: ConfigReader[T]): MakeDSLNamedAfterFrom[T] = {
      dsl.named(path).tagged(ConfTag(path)).from(decodeConfig[T](path))
    }
  }

  private[this] def unpackResult[T: Tag](config: AppConfig, path: String)(t: => Try[T]): T = {
    Try(t).flatten match {
      case Failure(exception) =>
        throw new DIConfigReadException(
          s"""Couldn't read configuration type at path="$path" as type `${Tag[T].tag}` due to error:
             |
             |  ${exception.getMessage}
             |
             |Config was: $config
             |""".stripMargin, exception)
      case Success(value) =>
        value
    }
  }

}
