package com.github.pshirshov.izumi.distage.roles.impl

import com.github.pshirshov.izumi.distage.app.{ApplicationBootstrapStrategy, BootstrapContext}
import com.github.pshirshov.izumi.distage.roles.launcher.RoleAppBootstrapStrategy.Using
import com.github.pshirshov.izumi.distage.roles.launcher.{RoleApp, RoleAppBootstrapStrategy}
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks

// TODO
trait ScoptRoleApp {
  this: RoleApp =>

  override type CommandlineConfig = ScoptLauncherArgs
  override type StrategyArgs = RoleAppBootstrapStrategyArgs

  protected def using: Seq[Using]

  override protected def commandlineSetup(args: Array[String]): ScoptRoleApp#CommandlineConfig = {
    ScoptLauncherArgs.parser.parse(args, ScoptLauncherArgs())
      .getOrElse {
        ScoptLauncherArgs.parser.showUsageAsError()
        throw new IllegalArgumentException("Unexpected commandline parameters")
      }
  }

  override protected def paramsToStrategyArgs(params: CommandlineConfig): StrategyArgs = {
    val args = ScoptRoleAppBootstrapArgs(params)
    args.copy(using = args.using ++ using)
  }


  override protected def setupContext(params: ScoptRoleApp#CommandlineConfig, args: ScoptRoleApp#StrategyArgs): ApplicationBootstrapStrategy = {
    Quirks.discard(params)
    val bsContext: BootstrapContext = BootstrapContext.BootstrapContextDefaultImpl(pluginConfig)

    new RoleAppBootstrapStrategy(args, bsContext)
      .init()
  }
}
