package com.github.pshirshov.izumi.distage.roles.services

import com.github.pshirshov.izumi.distage.config.model.AppConfig
import com.github.pshirshov.izumi.distage.roles.logger.SimpleLoggerConfigurator
import com.github.pshirshov.izumi.fundamentals.platform.cli.RoleAppArguments
import com.github.pshirshov.izumi.logstage.api.Log.Level
import com.github.pshirshov.izumi.logstage.api.{IzLogger, Log}

class EarlyLoggers() {

  import com.github.pshirshov.izumi.distage.roles.RoleAppLauncher._

  private val defaultLogLevel = Log.Level.Info
  private val defaultLogFormatJson = false

  def makeEarlyLogger(parameters: RoleAppArguments): IzLogger = {
    val rootLogLevel = getRootLogLevel(parameters)
    IzLogger(rootLogLevel)("phase" -> "early")
  }

  def makeLateLogger(parameters: RoleAppArguments, earlyLogger: IzLogger, config: AppConfig): IzLogger = {
    val rootLogLevel = getRootLogLevel(parameters)
    val logJson = getLogFormatJson(parameters)
    val router = new SimpleLoggerConfigurator(earlyLogger)
      .makeLogRouter(
        config.config.getConfig("logger")
        , rootLogLevel
        , logJson
      )

    IzLogger(router)("phase" -> "late")
  }

  private def getRootLogLevel(parameters: RoleAppArguments): Level = {
    logLevelRootParam.findValue(parameters.globalParameters)
      .map(v => {
        Log.Level.parseSafe(v.value, defaultLogLevel)
      })
      .getOrElse(defaultLogLevel)
  }

  private def getLogFormatJson(parameters: RoleAppArguments): Boolean = {
    logFormatParam.findValue(parameters.globalParameters)
      .map(v => {
        v.value match {
          case "json" => true
          case _ => false
        }
      })
      .getOrElse(defaultLogFormatJson)
  }

}
