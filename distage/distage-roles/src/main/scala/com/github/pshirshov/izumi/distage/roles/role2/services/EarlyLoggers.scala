package com.github.pshirshov.izumi.distage.roles.role2.services

import com.github.pshirshov.izumi.distage.config.SimpleLoggerConfigurator
import com.github.pshirshov.izumi.distage.config.model.AppConfig
import com.github.pshirshov.izumi.distage.roles.cli.RoleAppArguments
import com.github.pshirshov.izumi.logstage.api.Log.Level
import com.github.pshirshov.izumi.logstage.api.{IzLogger, Log}

class EarlyLoggers() {
  import com.github.pshirshov.izumi.distage.roles.role2.RoleAppLauncher._

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
    parameters.globalParameters.values
      .find(p => logLevelRootParam.matches(p.name))
      .map(v => {
        Log.Level.parseSafe(v.value, defaultLogLevel)
      })
      .getOrElse(defaultLogLevel)
  }

  private def getLogFormatJson(parameters: RoleAppArguments): Boolean = {
    parameters.globalParameters.values
      .find(p => logFormatParam.matches(p.name))
      .map(v => {
        v.value match {
          case "json" => true
          case _ => false
        }
      })
      .getOrElse(defaultLogFormatJson)
  }

}
