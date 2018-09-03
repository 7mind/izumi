package com.github.pshirshov.izumi.distage.roles.impl

import com.github.pshirshov.izumi.distage.app.BootstrapContextDefaultImpl
import com.github.pshirshov.izumi.distage.roles.launcher.RoleAppBootstrapStrategy.Using
import com.github.pshirshov.izumi.distage.roles.launcher.{RoleApp, RoleAppBootstrapStrategy}

// TODO
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

  override protected def paramsToStrategyArgs(params: CommandlineConfig): StrategyArgs = {
    val args = ScoptRoleAppBootstrapArgs(params)
    args.copy(using = args.using ++ using)
  }

  override protected def setupContext(params: CommandlineConfig, args: StrategyArgs): Strategy = {
    val bsContext: BootstrapContext = BootstrapContextDefaultImpl(
      params, pluginConfig
    )

    new RoleAppBootstrapStrategy[CommandlineConfig](args, bsContext)
      .init()
  }

}
