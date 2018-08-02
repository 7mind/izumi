package com.github.pshirshov.izumi.distage.roles.launcher

import com.github.pshirshov.izumi.distage.app
import com.github.pshirshov.izumi.distage.app.OpinionatedDiApp
import com.github.pshirshov.izumi.distage.config.model.AppConfig
import com.github.pshirshov.izumi.distage.model.Locator
import com.github.pshirshov.izumi.distage.plugins.load.PluginLoaderDefaultImpl.PluginConfig

abstract class RoleApp extends OpinionatedDiApp {

  type StrategyArgs

  def bootstrapConfig: PluginConfig =
    PluginConfig(
        debug = false
        , Seq.empty // there are no bootstrap plugins in Izumi, no need to scan
        , Seq.empty
      )

  override protected def commandlineSetup(args: Array[String]): Strategy = {
    val params = parseArgs(args)
    val strategyArgs = paramsToStrategyArgs(params)
    val appConfig = buildConfig(params)
    setupContext(params, strategyArgs, appConfig)
  }

  def pluginConfig: PluginConfig

  protected def parseArgs(args: Array[String]): CommandlineConfig

  protected def buildConfig(params: CommandlineConfig): AppConfig

  protected def paramsToStrategyArgs(params: CommandlineConfig): StrategyArgs

  protected def setupContext(params: CommandlineConfig, args: StrategyArgs, appConfig: AppConfig): Strategy

  override protected def start(context: Locator, bootstrapContext: app.BootstrapContext[CommandlineConfig]): Unit = {
    context.get[RoleStarter].start(context)
  }

}

