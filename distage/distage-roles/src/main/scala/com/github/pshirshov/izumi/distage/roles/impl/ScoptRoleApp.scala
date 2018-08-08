package com.github.pshirshov.izumi.distage.roles.impl

import com.github.pshirshov.izumi.distage.app.BootstrapContextDefaultImpl
import com.github.pshirshov.izumi.distage.config.model.AppConfig
import com.github.pshirshov.izumi.distage.roles.launcher.RoleAppBootstrapStrategy.Using
import com.github.pshirshov.izumi.distage.roles.launcher.{RoleApp, RoleAppBootstrapStrategy}
import com.typesafe.config.ConfigFactory

import scala.collection.JavaConverters._

// FIXME
trait ScoptRoleApp {
  this: RoleApp =>

  override type CommandlineConfig = ScoptLauncherArgs
  override type StrategyArgs = RoleAppBootstrapStrategyArgs

  protected def using: Seq[Using]

  override protected def parseArgs(args: Array[String]): ScoptRoleApp#CommandlineConfig =
    ScoptLauncherArgs.parser.parse(args, ScoptLauncherArgs())
      .getOrElse {
        ScoptLauncherArgs.parser.showUsageAsError()
        throw new IllegalArgumentException("Unexpected commandline parameters")
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

  override protected def paramsToStrategyArgs(params: CommandlineConfig): StrategyArgs = {
    val args = ScoptRoleAppBootstrapArgs(params)
    args.copy(using = args.using ++ using)
  }

  override protected def setupContext(params: CommandlineConfig, args: StrategyArgs, appConfig: AppConfig): Strategy = {
    val bsContext: BootstrapContext = BootstrapContextDefaultImpl(
      params, bootstrapConfig, pluginConfig, appConfig
    )

    new RoleAppBootstrapStrategy[CommandlineConfig](args, bsContext)
      .init()
  }

}
