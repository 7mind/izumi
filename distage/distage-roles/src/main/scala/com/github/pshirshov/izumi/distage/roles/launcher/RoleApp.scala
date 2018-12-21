package com.github.pshirshov.izumi.distage.roles.launcher

import com.github.pshirshov.izumi.distage.app.{ApplicationBootstrapStrategy, OpinionatedDiApp}
import com.github.pshirshov.izumi.distage.model.Locator
import com.github.pshirshov.izumi.distage.plugins.load.PluginLoaderDefaultImpl.PluginConfig
import com.github.pshirshov.izumi.distage.roles.roles.RoleStarter

abstract class RoleApp extends OpinionatedDiApp {

  type StrategyArgs

  override protected def commandlineSetup(args: Array[String]): CommandlineConfig

  def pluginConfig: PluginConfig

  protected def paramsToStrategyArgs(params: CommandlineConfig): StrategyArgs


  protected def makeStrategy(cliConfig: CommandlineConfig): ApplicationBootstrapStrategy = {
    val strategyArgs = paramsToStrategyArgs(cliConfig)
    setupContext(cliConfig, strategyArgs)
  }

  protected def setupContext(params: CommandlineConfig, args: StrategyArgs): ApplicationBootstrapStrategy

  override protected def start(context: Locator): Unit = {
    val starter = context.get[RoleStarter]
    starter.start()
    starter.join()
  }

}

