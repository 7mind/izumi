package izumi.distage.roles.services

import com.typesafe.config.ConfigFactory
import izumi.distage.config.model.AppConfig
import izumi.distage.roles.RoleAppLauncher.Options
import izumi.distage.roles.logger.SimpleLoggerConfigurator
import izumi.fundamentals.platform.cli.model.raw.{RawAppArgs, RawEntrypointParams}
import izumi.logstage.api.Log.Level
import izumi.logstage.api.{IzLogger, Log}

import scala.util.Try

object EarlyLoggers {

  def makeEarlyLogger(parameters: RawAppArgs, defaultLogLevel: Log.Level): IzLogger = {
    val rootLogLevel = getRootLogLevel(parameters.globalParameters, defaultLogLevel)
    IzLogger(rootLogLevel)("phase" -> "early")
  }

  def makeLateLogger(parameters: RawAppArgs, earlyLogger: IzLogger, config: AppConfig, defaultLogLevel: Log.Level, defaultLogFormatJson: Boolean): IzLogger = {
    val rootLogLevel = getRootLogLevel(parameters.globalParameters, defaultLogLevel)
    val logJson = getLogFormatJson(parameters.globalParameters, defaultLogFormatJson)
    val loggerConfig = Try(config.config.getConfig("logger")).getOrElse(ConfigFactory.empty("Couldn't parse `logger` configuration"))
    val router = new SimpleLoggerConfigurator(earlyLogger)
      .makeLogRouter(
        config = loggerConfig,
        root = rootLogLevel,
        json = logJson,
      )

    IzLogger(router)("phase" -> "late")
  }

  private def getRootLogLevel(parameters: RawEntrypointParams, defaultLogLevel: Log.Level): Level = {
    parameters.findValue(Options.logLevelRootParam)
      .map(v => Log.Level.parseSafe(v.value, defaultLogLevel))
      .getOrElse(defaultLogLevel)
  }

  private def getLogFormatJson(parameters: RawEntrypointParams, defaultLogFormatJson: Boolean): Boolean = {
    parameters.findValue(Options.logFormatParam)
      .map(_.value == "json")
      .getOrElse(defaultLogFormatJson)
  }

}
