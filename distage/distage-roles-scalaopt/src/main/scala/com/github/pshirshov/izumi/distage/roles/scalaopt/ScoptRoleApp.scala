package com.github.pshirshov.izumi.distage.roles.scalaopt

import com.github.pshirshov.izumi.distage.app.{ApplicationBootstrapStrategy, BootstrapConfig}
import com.github.pshirshov.izumi.distage.model.definition.BindingTag
import com.github.pshirshov.izumi.distage.roles.BackendPluginTags
import com.github.pshirshov.izumi.distage.roles.impl.RoleAppBootstrapStrategyArgs
import com.github.pshirshov.izumi.distage.roles.launcher.RoleAppBootstrapStrategy.Using
import com.github.pshirshov.izumi.distage.roles.launcher.{RoleApp, RoleAppBootstrapStrategy}
import com.github.pshirshov.izumi.distage.roles.scalaopt.ScoptLauncherArgs.{IzOptionParser, ParserExtenstion}
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks
import scopt.Zero

abstract class ScoptRoleApp[T <: ScoptLauncherArgs: Zero] extends RoleApp {

  override type CommandlineConfig = T

  override type StrategyArgs = RoleAppBootstrapStrategyArgs

  protected def using: Seq[Using]

  protected def externalParsers : Set[ParserExtenstion[ScoptRoleApp[T]#CommandlineConfig]] = Set.empty

  protected lazy val parser: IzOptionParser[ScoptRoleApp[T]#CommandlineConfig] = new IzOptionParser[ScoptRoleApp[T]#CommandlineConfig](externalParsers)

  override protected def commandlineSetup(args: Array[String]): ScoptRoleApp[T]#CommandlineConfig = {
    parser.parse(args, implicitly[Zero[T]].zero)
      .getOrElse {
        parser.showUsageAsError()
        throw new IllegalArgumentException("Unexpected commandline parameters")
      }
  }

  protected def tagDisablingStrategy(params: ScoptRoleApp[T]#CommandlineConfig): BindingTag.Expressions.Composite = {
    filterProductionTags(params.dummyStorage)
  }

  protected def filterProductionTags(useDummy: Option[Boolean]) : BindingTag.Expressions.Composite = {
    if (useDummy.contains(true)) {
      BindingTag.Expressions.all(BackendPluginTags.Production, BackendPluginTags.Storage)
    } else {
      BindingTag.Expressions.any(BackendPluginTags.Test, BackendPluginTags.Dummy)
    }
  }

  override protected def paramsToStrategyArgs(params: ScoptRoleApp[T]#CommandlineConfig): StrategyArgs = {
    val args = ScoptRoleAppBootstrapArgs[ScoptRoleApp[T]#CommandlineConfig](params, tagDisablingStrategy(params))
    args.copy(using = args.using ++ using)
  }

  override protected def setupContext(params: ScoptRoleApp[T]#CommandlineConfig, args: ScoptRoleApp[T]#StrategyArgs): ApplicationBootstrapStrategy = {
    Quirks.discard(params)
    val bsContext: BootstrapConfig = BootstrapConfig(pluginConfig)

    new RoleAppBootstrapStrategy(args, bsContext)
      .init()
  }
}
