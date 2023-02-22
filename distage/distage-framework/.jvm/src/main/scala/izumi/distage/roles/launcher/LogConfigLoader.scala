package izumi.distage.roles.launcher

import distage.Id
import distage.config.{AppConfig, DIConfigReader}
import izumi.distage.roles.launcher.LogConfigLoader.DeclarativeLoggerConfig
import izumi.logstage.api.Log
import izumi.logstage.api.Log.Level.Warn
import izumi.logstage.api.Log.Message
import izumi.logstage.api.rendering.RenderingOptions
import logstage.IzLogger

import scala.util.Try

trait LogConfigLoader {
  def loadLoggingConfig(config: AppConfig): DeclarativeLoggerConfig
}

object LogConfigLoader {
  sealed trait LoggerFormat

  object LoggerFormat {
    case object Json extends LoggerFormat

    case object Text extends LoggerFormat
  }

  case class DeclarativeLoggerConfig(
    format: LoggerFormat,
    rendering: RenderingOptions,
    levels: Map[String, Log.Level],
    rootLevel: Log.Level,
    interceptJUL: Boolean,
  )

  final case class SinksConfig(
    levels: Map[String, List[String]],
    options: Option[RenderingOptions],
    json: Option[Boolean],
    jul: Option[Boolean],
  )

  object SinksConfig {
    implicit val configReader: DIConfigReader[SinksConfig] = DIConfigReader.derived
  }

  class LogConfigLoaderImpl(cliOptions: CLILoggerOptions, earlyLogger: IzLogger @Id("early")) extends LogConfigLoader {
    def loadLoggingConfig(config: AppConfig): DeclarativeLoggerConfig = {
      val logconf = readConfig(config)
      val isJson = cliOptions.json || logconf.json.contains(true)
      val options = logconf.options.getOrElse(RenderingOptions.default)
      val jul = logconf.jul.getOrElse(true)

      val levels = logconf.levels.flatMap {
        case (stringLevel, packageList) =>
          val level = Log.Level.parseLetter(stringLevel)
          packageList.map(pkg => (pkg, level))
      }

      val format = if (isJson) {
        LoggerFormat.Json
      } else {
        LoggerFormat.Text
      }

      val fullConfig = DeclarativeLoggerConfig(format, options, levels, cliOptions.level, jul)
      fullConfig
    }

    private[this] def readConfig(config: AppConfig): SinksConfig = {
      Try(config.config.getConfig("logger")).toEither.left
        .map(_ => Message("No `logger` section in config. Using defaults."))
        .flatMap {
          config =>
            SinksConfig.configReader.decodeConfig(config).toEither.left.map {
              exception =>
                Message(s"Failed to parse `logger` config section into ${classOf[SinksConfig] -> "type"}. Using defaults. $exception")
            }
        } match {
        case Left(errMessage) =>
          earlyLogger.log(Warn)(errMessage)
          SinksConfig(Map.empty, None, None, None)

        case Right(value) =>
          value
      }
    }
  }
}
