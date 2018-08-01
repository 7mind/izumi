package com.github.pshirshov.izumi.distage.roles.launcher

import com.github.pshirshov.izumi.distage.app
import com.github.pshirshov.izumi.distage.app.OpinionatedDiApp
import com.github.pshirshov.izumi.distage.config.model.AppConfig
import com.github.pshirshov.izumi.distage.model.Locator
import com.github.pshirshov.izumi.distage.plugins.load.PluginLoaderDefaultImpl.PluginConfig
import com.typesafe.config.ConfigFactory

import scala.collection.JavaConverters._

abstract class RoleApp extends OpinionatedDiApp {
  override type CommandlineConfig = RoleLauncherArgs

  def pluginConfig: PluginConfig

  def bootstrapConfig: PluginConfig =
    PluginConfig(
        debug = false
        , Seq.empty // there are no bootstrap plugins in Izumi, no need to scan
        , Seq.empty
      )

  override protected def commandlineSetup(args: Array[String]): Strategy = {
    RoleLauncherArgs.parser.parse(args, RoleLauncherArgs()) match {
      case Some(params) =>
        val appConfig: AppConfig = buildConfig(params)

        val bsContext: BootstrapContext = app.BootstrapContextDefaultImpl(
          params, bootstrapConfig, pluginConfig, appConfig
        )

        new RoleAppBootstrapStrategy(params, bsContext).init()
      case _ =>
        RoleLauncherArgs.parser.showUsageAsError()
        throw new IllegalArgumentException(s"Unexpected commandline parameters")
    }
  }

  private def buildConfig(params: RoleLauncherArgs): AppConfig = {
    val commonConfig = params.configFile.map(ConfigFactory.parseFile)
      .getOrElse(ConfigFactory.defaultApplication())

    val appConfig = ConfigFactory.load(commonConfig)
    val rolesConfig = params.roles.flatMap(_.configFile).map(ConfigFactory.parseFile)

    val mainConfig = rolesConfig.foldLeft(appConfig) {
      case (main, role) =>
        val duplicateKeys = main.entrySet().asScala.map(_.getKey).intersect(role.entrySet().asScala.map(_.getKey))
        if (duplicateKeys.nonEmpty) {
          throw new IllegalArgumentException(s"Found duplicates keys in supplied configs: $duplicateKeys")
        }
        main.withFallback(role)
    }

    AppConfig(mainConfig)
  }

  override protected def start(context: Locator, bootstrapContext: app.BootstrapContext[RoleLauncherArgs]): Unit = {
    context.get[RoleStarter].start(context)
  }

}

