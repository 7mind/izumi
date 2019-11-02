package izumi.distage.roles.services

import izumi.distage.config.model.AppConfig
import izumi.distage.roles.logger.SimpleLoggerConfigurator
import izumi.fundamentals.platform.cli.model.raw.RawAppArgs
import izumi.logstage.api.Log.Level
import izumi.logstage.api.{IzLogger, Log}

class EarlyLoggers() {

  import izumi.distage.roles.RoleAppLauncher._

  private val defaultLogLevel = Log.Level.Info
  private val defaultLogFormatJson = false

  def makeEarlyLogger(parameters: RawAppArgs): IzLogger = {
    val rootLogLevel = getRootLogLevel(parameters)
    IzLogger(rootLogLevel)("phase" -> "early")
  }

  def makeLateLogger(parameters: RawAppArgs, earlyLogger: IzLogger, config: AppConfig): IzLogger = {
    val rootLogLevel = getRootLogLevel(parameters)
    val logJson = getLogFormatJson(parameters)
    val router = new SimpleLoggerConfigurator(earlyLogger).makeLogRouter(
      config = config.config.getConfig("logger"),
      root = rootLogLevel,
      json = logJson,
    )

    IzLogger(router)("phase" -> "late")
  }

  private def getRootLogLevel(parameters: RawAppArgs): Level = {
    Options.logLevelRootParam.findValue(parameters.globalParameters)
      .map(v => {
        Log.Level.parseSafe(v.value, defaultLogLevel)
      })
      .getOrElse(defaultLogLevel)
  }

  private def getLogFormatJson(parameters: RawAppArgs): Boolean = {
    Options.logFormatParam.findValue(parameters.globalParameters)
      .map(v => {
        v.value match {
          case "json" => true
          case _ => false
        }
      })
      .getOrElse(defaultLogFormatJson)
  }

}
