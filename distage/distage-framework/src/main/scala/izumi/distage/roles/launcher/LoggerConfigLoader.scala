package izumi.distage.roles.launcher

import distage.Id
import distage.config.{AppConfig, DIConfigReader}
import izumi.distage.config.DistageConfigImpl
import izumi.distage.roles.launcher.LoggerConfigLoader.DeclarativeLoggerConfig
import izumi.logstage.api.Log
import izumi.logstage.api.Log.Level.Warn
import izumi.logstage.api.Log.Message
import izumi.logstage.api.rendering.RenderingOptions
import logstage.IzLogger

trait LoggerConfigLoader {
  def loadLoggingConfig(config: AppConfig): DeclarativeLoggerConfig
}

object LoggerConfigLoader {

  sealed trait LoggerFormat
  object LoggerFormat {
    case object Json extends LoggerFormat
    case object Text extends LoggerFormat
  }

  final case class DeclarativeLoggerConfig(
    format: LoggerFormat,
    rendering: RenderingOptions,
    levels: Map[String, Log.Level],
    rootLevel: Log.Level,
    interceptJUL: Boolean,
  )

  final case class HoconSinksSection(
    levels: Map[String, List[String]],
    options: Option[RenderingOptions],
    json: Option[Boolean],
    jul: Option[Boolean],
  )

  object HoconSinksSection {
    implicit val configReader: DIConfigReader[HoconSinksSection] = DIConfigReader.derived
  }

  class LogConfigLoaderImpl(cliOptions: CLILoggerOptions, earlyLogger: IzLogger @Id("early")) extends LoggerConfigLoader {
    def loadLoggingConfig(config: AppConfig): DeclarativeLoggerConfig = {
      val logconf = readConfig(config)
      val isJson = cliOptions.json || logconf.json.contains(true)
      val options = logconf.options.getOrElse(RenderingOptions.default)
      val jul = logconf.jul.getOrElse(true)

      import izumi.fundamentals.collections.IzCollections.*
      val levels = logconf.levels.iterator
        .flatMap {
          case (stringLevel, packageList) =>
            val level = Log.Level.parseLetter(stringLevel)
            packageList.map {
              pkg =>
                (pkg, level)
            }
        }
        .toMultimapView
        .map {
          case (path, levels) =>
            (path, levels.min)
        }
        .toMap

      val format = if (isJson) {
        LoggerFormat.Json
      } else {
        LoggerFormat.Text
      }

      DeclarativeLoggerConfig(format, options, levels, cliOptions.level, jul)
    }

    private def readConfig(config: AppConfig): HoconSinksSection = {
      DistageConfigImpl
        .getConfig(config.config, "logger")
        .toRight(left = Message("No `logger` section in config. Using defaults."))
        .flatMap {
          config =>
            HoconSinksSection.configReader.decodeConfig(config).toEither.left.map {
              exception =>
                Message(s"Failed to parse `logger` config section into ${classOf[HoconSinksSection] -> "type"}. Using defaults. $exception")
            }
        } match {
        case Left(errMessage) =>
          earlyLogger.log(Warn)(errMessage)
          HoconSinksSection(Map.empty, None, None, None)

        case Right(value) =>
          value
      }
    }
  }

}
