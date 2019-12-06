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
  private[this] final val defaultLogLevel = Log.Level.Info
  private[this] final val defaultLogFormatJson = false

  def makeEarlyLogger(parameters: RawAppArgs): IzLogger = {
    val rootLogLevel = getRootLogLevel(parameters.globalParameters)
    IzLogger(rootLogLevel)("phase" -> "early")
  }

  def makeLateLogger(parameters: RawAppArgs, earlyLogger: IzLogger, config: AppConfig): IzLogger = {
    val rootLogLevel = getRootLogLevel(parameters.globalParameters)
    val logJson = getLogFormatJson(parameters.globalParameters)
    val loggerConfig = Try(config.config.getConfig("logger")).getOrElse(ConfigFactory.empty("Couldn't parse `logger` configuration"))
    val router = new SimpleLoggerConfigurator(earlyLogger)
      .makeLogRouter(
        config = loggerConfig,
        root = rootLogLevel,
        json = logJson,
      )

    IzLogger(router)("phase" -> "late")
  }

  private def getRootLogLevel(parameters: RawEntrypointParams): Level = {
    parameters.findValue(Options.logLevelRootParam)
      .map(v => Log.Level.parseSafe(v.value, defaultLogLevel))
      .getOrElse(defaultLogLevel)
  }

  private def getLogFormatJson(parameters: RawEntrypointParams): Boolean = {
    parameters.findValue(Options.logFormatParam)
      .map(_.value == "json")
      .getOrElse(defaultLogFormatJson)
  }

}
