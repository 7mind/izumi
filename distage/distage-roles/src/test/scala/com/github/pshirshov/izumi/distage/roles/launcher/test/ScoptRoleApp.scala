package com.github.pshirshov.izumi.distage.roles.launcher.test

import com.github.pshirshov.izumi.distage.app.BootstrapContextDefaultImpl
import com.github.pshirshov.izumi.distage.config.model.AppConfig
import com.github.pshirshov.izumi.distage.roles.launcher.{RoleApp, RoleAppBootstrapStrategy}
import com.typesafe.config.ConfigFactory

import scala.collection.JavaConverters._

trait ScoptRoleApp {
  this: RoleApp =>

  override type CommandlineConfig = ScoptLauncherArgs

  override protected def parseArgs(args: Array[String]): ScoptRoleApp#CommandlineConfig =
    ScoptLauncherArgs.parser.parse(args, ScoptLauncherArgs())
      .getOrElse {
        ScoptLauncherArgs.parser.showUsageAsError()
        throw new IllegalArgumentException(s"Unexpected commandline parameters")
      }

  override protected def buildConfig(params: ScoptRoleApp#CommandlineConfig): AppConfig = {
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

  override protected def setupContext(params: CommandlineConfig, appConfig: AppConfig): Strategy = {
    val bsContext: BootstrapContext = BootstrapContextDefaultImpl(
      params, bootstrapConfig, pluginConfig, appConfig
    )

    val args = ScoptRoleAppBootstrapArgs(params)
    import args.{disabledTags, roleSet, jsonLogging, rootLogLevel, using, addOverrides}

    new RoleAppBootstrapStrategy[CommandlineConfig](
      disabledTags, roleSet, jsonLogging, rootLogLevel, using, addOverrides
      , bsContext
    ).init()
  }

}
