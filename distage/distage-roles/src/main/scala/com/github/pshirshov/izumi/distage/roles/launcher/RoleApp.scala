package com.github.pshirshov.izumi.distage.roles.launcher

import com.github.pshirshov.izumi.distage.app
import com.github.pshirshov.izumi.distage.app.OpinionatedDiApp
import com.github.pshirshov.izumi.distage.model.Locator
import com.github.pshirshov.izumi.distage.plugins.load.PluginLoaderDefaultImpl.PluginConfig

abstract class RoleApp extends OpinionatedDiApp {

  type StrategyArgs

  override protected def commandlineSetup(args: Array[String]): Strategy = {
    val params = parseArgs(args)
    val strategyArgs = paramsToStrategyArgs(params)
    setupContext(params, strategyArgs)
  }

  def pluginConfig: PluginConfig

  protected def parseArgs(args: Array[String]): CommandlineConfig

  protected def paramsToStrategyArgs(params: CommandlineConfig): StrategyArgs

  protected def setupContext(params: CommandlineConfig, args: StrategyArgs): Strategy

  override protected def start(context: Locator, bootstrapContext: app.BootstrapContext[CommandlineConfig]): Unit = {
    val starter = context.get[RoleStarter]
    starter.start()
    starter.join()
  }

}

