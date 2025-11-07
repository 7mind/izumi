package izumi.distage.roles.launcher

import distage.Id
import distage.config.AppConfig
import izumi.distage.roles.launcher.LogConfigLoader.DeclarativeLoggerConfig
import izumi.logstage.api.Log
import izumi.logstage.api.rendering.RenderingOptions
import logstage.IzLogger

import scala.annotation.unused

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

  // Simplified JS implementation - no HOCON config parsing
  class LogConfigLoaderImpl(cliOptions: CLILoggerOptions, @unused earlyLogger: IzLogger @Id("early")) extends LogConfigLoader {
    def loadLoggingConfig(config: AppConfig): DeclarativeLoggerConfig = {
      val isJson = cliOptions.json
      val options = RenderingOptions.default
      val jul = false // No JUL interception on JS

      val format = if (isJson) {
        LoggerFormat.Json
      } else {
        LoggerFormat.Text
      }

      DeclarativeLoggerConfig(format, options, Map.empty, cliOptions.level, jul)
    }
  }
}
