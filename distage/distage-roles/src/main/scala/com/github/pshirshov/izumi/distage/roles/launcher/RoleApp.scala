package com.github.pshirshov.izumi.distage.roles.launcher

import com.github.pshirshov.izumi.distage.app
import com.github.pshirshov.izumi.distage.app.OpinionatedDiApp
import com.github.pshirshov.izumi.distage.config.model.AppConfig
import com.github.pshirshov.izumi.distage.model.Locator
import com.github.pshirshov.izumi.distage.plugins.load.PluginLoaderDefaultImpl.PluginConfig

abstract class RoleApp extends OpinionatedDiApp {
//  override type CommandlineConfig = RoleLauncherArgs

  def bootstrapConfig: PluginConfig =
    PluginConfig(
        debug = false
        , Seq.empty // there are no bootstrap plugins in Izumi, no need to scan
        , Seq.empty
      )

  override protected def commandlineSetup(args: Array[String]): Strategy = {
    val params = parseArgs(args)
    val appConfig = buildConfig(params)
    setupContext(params, appConfig)
  }

  def pluginConfig: PluginConfig

  protected def parseArgs(args: Array[String]): CommandlineConfig

  protected def buildConfig(params: CommandlineConfig): AppConfig

  protected def setupContext(params: CommandlineConfig, appConfig: AppConfig): Strategy

  override protected def start(context: Locator, bootstrapContext: app.BootstrapContext[CommandlineConfig]): Unit = {
    context.get[RoleStarter].start(context)
  }

}

