package izumi.distage.roles.launcher

import izumi.distage.config.model.AppConfig
import izumi.distage.roles.RoleAppMain
import izumi.fundamentals.platform.cli.model.raw.{RawAppArgs, RawEntrypointParams}
import izumi.logstage.api.Log.Level
import izumi.logstage.api.logger.LogRouter
import izumi.logstage.api.{IzLogger, Log}

object EarlyLoggers {

  def makeEarlyLogger(parameters: RawAppArgs, defaultLogLevel: Log.Level): IzLogger = {
    val rootLogLevel = getRootLogLevel(parameters.globalParameters, defaultLogLevel)
    IzLogger(rootLogLevel)("phase" -> "early")
  }

  def makeLateLogRouter(parameters: RawAppArgs, earlyLogger: IzLogger, config: AppConfig, defaultLogLevel: Log.Level, defaultLogFormatJson: Boolean): LogRouter = {
    val rootLogLevel = getRootLogLevel(parameters.globalParameters, defaultLogLevel)
    val logJson = getLogFormatJson(parameters.globalParameters, defaultLogFormatJson)
    new SimpleLoggerConfigurator(earlyLogger).makeLogRouter(config.config, rootLogLevel, logJson)
  }

  private def getRootLogLevel(parameters: RawEntrypointParams, defaultLogLevel: Log.Level): Level = {
    parameters
      .findValue(RoleAppMain.Options.logLevelRootParam)
      .map(v => Log.Level.parseSafe(v.value, defaultLogLevel))
      .getOrElse(defaultLogLevel)
  }

  private def getLogFormatJson(parameters: RawEntrypointParams, defaultLogFormatJson: Boolean): Boolean = {
    parameters
      .findValue(RoleAppMain.Options.logFormatParam)
      .map(_.value == "json")
      .getOrElse(defaultLogFormatJson)
  }

}
