package izumi.distage.roles.services

import com.typesafe.config.ConfigFactory
import izumi.distage.config.model.AppConfig
import izumi.distage.roles.RoleAppLauncher.Options
import izumi.distage.roles.logger.SimpleLoggerConfigurator
import izumi.fundamentals.platform.cli.model.raw.RawAppArgs
import izumi.logstage.api.Log.Level
import izumi.logstage.api.{IzLogger, Log}

import scala.util.Try

object EarlyLoggers {
  private[this] final val defaultLogLevel = Log.Level.Info
  private[this] final val defaultLogFormatJson = false

  def makeEarlyLogger(parameters: RawAppArgs): IzLogger = {
    val rootLogLevel = getRootLogLevel(parameters)
    IzLogger(rootLogLevel)("phase" -> "early")
  }

  def makeLateLogger(parameters: RawAppArgs, earlyLogger: IzLogger, config: AppConfig): IzLogger = {
    val rootLogLevel = getRootLogLevel(parameters)
    val logJson = getLogFormatJson(parameters)
    val loggerConfig = Try(config.config.getConfig("logger")).getOrElse(ConfigFactory.empty("Couldn't parse `logger` configuration"))
    val router = new SimpleLoggerConfigurator(earlyLogger)
      .makeLogRouter(
        config = loggerConfig,
        root = rootLogLevel,
        json = logJson,
      )

    IzLogger(router)("phase" -> "late")
  }

  private def getRootLogLevel(parameters: RawAppArgs): Level = {
    Options.logLevelRootParam.findValue(parameters.globalParameters)
      .map(v => Log.Level.parseSafe(v.value, defaultLogLevel))
      .getOrElse(defaultLogLevel)
  }

  private def getLogFormatJson(parameters: RawAppArgs): Boolean = {
    Options.logFormatParam.findValue(parameters.globalParameters)
      .map(_.value == "json")
      .getOrElse(defaultLogFormatJson)
  }

}
