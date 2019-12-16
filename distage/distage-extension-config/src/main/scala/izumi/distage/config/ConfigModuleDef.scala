package izumi.distage.config

import com.typesafe.config.Config
import izumi.distage.config.codec.ConfigReader
import izumi.distage.config.model.AppConfig
import izumi.distage.config.model.exceptions.DIConfigReadException
import izumi.distage.model.definition.BindingTag.ConfTag
import izumi.distage.model.definition.dsl.ModuleDefDSL.{MakeDSL, MultipleDSL, MultipleNamedDSL}
import izumi.distage.model.definition.{BootstrapModuleDef, ModuleDef}
import izumi.distage.model.planning.PlanningHook
import izumi.fundamentals.platform.language.CodePositionMaterializer
import izumi.fundamentals.reflection.Tags.Tag

import scala.util.{Failure, Success, Try}

class AppConfigModule(appConfig: AppConfig) extends ModuleDef {
  def this(config: Config) = this(AppConfig(config))

  make[AppConfig].fromValue(appConfig)
}

object AppConfigModule {
  def apply(appConfig: AppConfig): AppConfigModule = new AppConfigModule(appConfig)
  def apply(config: Config): AppConfigModule = new AppConfigModule(config)
}

class ConfigPathExtractorModule extends BootstrapModuleDef {
  many[PlanningHook]
    .add[ConfigPathExtractor]
}

trait ConfigModuleDef extends ModuleDef {
  final def makeConfig[T: Tag: ConfigReader](path: String)(implicit pos: CodePositionMaterializer): MultipleDSL[T] = {
    bind[T] {
      config: AppConfig =>
        unpackResult(config, path)(ConfigReader[T].apply(config.config.getValue(path)))
    }.tagged(ConfTag(path))
  }
  final def makeConfigNamed[T: Tag: ConfigReader](path: String)(implicit pos: CodePositionMaterializer): MultipleNamedDSL[T] = {
    makeConfig[T](path).named(path)
  }

  implicit final class FromConfig[T](private val dsl: MakeDSL[T]) {
    def fromConfig(path: String)(implicit dec: ConfigReader[T], tag: Tag[T]): Unit = {
      dsl.tagged(ConfTag(path)).from {
        config: AppConfig =>
          unpackResult(config, path)(ConfigReader[T].apply(config.config.getValue(path)))
      }
    }
    def fromConfigNamed(path: String)(implicit dec: ConfigReader[T], tag: Tag[T]): Unit = {
      dsl.named(path).tagged(ConfTag(path)).from {
        config: AppConfig =>
          unpackResult(config, path)(ConfigReader[T].apply(config.config.getValue(path)))
      }
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
