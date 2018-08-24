package com.github.pshirshov.izumi.distage.app

import com.github.pshirshov.izumi.distage.config.model.AppConfig
import com.github.pshirshov.izumi.distage.model.Locator
import com.github.pshirshov.izumi.distage.model.definition.{BootstrapModule, BootstrapModuleDef, ModuleBase}
import com.github.pshirshov.izumi.distage.model.exceptions.DIException
import com.github.pshirshov.izumi.distage.plugins._
import com.github.pshirshov.izumi.distage.plugins.load.PluginLoaderDefaultImpl.PluginConfig
import com.github.pshirshov.izumi.distage.plugins.load.{PluginLoader, PluginLoaderDefaultImpl, PluginLoaderNullImpl}
import com.github.pshirshov.izumi.distage.plugins.merge.{PluginMergeStrategy, SimplePluginMergeStrategy}
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks
import com.github.pshirshov.izumi.logstage.api.IzLogger
import com.github.pshirshov.izumi.logstage.api.Log.CustomContext
import com.github.pshirshov.izumi.logstage.api.logger.LogRouter
import distage.Injector

// TODO: startables
// TODO: config mapping/injection
// TODO: cli parser?..
// TODO: split into di-plugins and di-app


trait BootstrapContext[CommandlineConfig] {
  def cliConfig: CommandlineConfig

  def pluginConfig: PluginConfig

  def appConfig: AppConfig
}

case class BootstrapContextDefaultImpl[CommandlineConfig]
(
  cliConfig: CommandlineConfig
  , pluginConfig: PluginConfig
  , appConfig: AppConfig
) extends BootstrapContext[CommandlineConfig]

trait ApplicationBootstrapStrategy[CommandlineConfig] {

  type Context = BootstrapContext[CommandlineConfig]

  def context: Context

  def mergeStrategy(bs: Seq[PluginBase], app: Seq[PluginBase]): PluginMergeStrategy[LoadedPlugins]

  def router(): LogRouter

  def bootstrapModules(bs: LoadedPlugins, app: LoadedPlugins): Seq[BootstrapModule]

  def appModules(bs: LoadedPlugins, app: LoadedPlugins): Seq[ModuleBase]

  def mkBootstrapLoader(): PluginLoader

  def mkLoader(): PluginLoader
}


abstract class ApplicationBootstrapStrategyBaseImpl[CommandlineConfig]
(
  override val context: BootstrapContext[CommandlineConfig]
) extends ApplicationBootstrapStrategy[CommandlineConfig] {
  def mergeStrategy(bs: Seq[PluginBase], app: Seq[PluginBase]): PluginMergeStrategy[LoadedPlugins] = {
    Quirks.discard(bs, app)
    SimplePluginMergeStrategy
  }

  def bootstrapModules(bs: LoadedPlugins, app: LoadedPlugins): Seq[BootstrapModule] = {
    Quirks.discard(bs, app)
    Seq.empty
  }

  def appModules(bs: LoadedPlugins, app: LoadedPlugins): Seq[ModuleBase] = {
    Quirks.discard(bs, app)
    Seq.empty
  }

  def mkBootstrapLoader(): PluginLoader = PluginLoaderNullImpl

  def mkLoader(): PluginLoader = new PluginLoaderDefaultImpl(context.pluginConfig)
}

abstract class OpinionatedDiApp {
  type CommandlineConfig

  type Strategy = ApplicationBootstrapStrategy[CommandlineConfig]
  type BootstrapContext = Strategy#Context

  def main(args: Array[String]): Unit = {
    try {
      doMain(commandlineSetup(args))
    } catch {
      case t: Throwable =>
        handler.onError(t)
    }
  }

  protected def commandlineSetup(args: Array[String]): Strategy

  protected def doMain(strategy: Strategy): Unit = {
    val loggerRouter = strategy.router()

    val logger = new IzLogger(loggerRouter, CustomContext.empty) // TODO: add instance/machine id here?
    val bootstrapLoader = strategy.mkBootstrapLoader()
    val appLoader = strategy.mkLoader()

    val bootstrapAutoDef = bootstrapLoader.load()
    val appAutoDef = appLoader.load()
    val mergeStrategy = strategy.mergeStrategy(bootstrapAutoDef, appAutoDef)

    val mergedBs = mergeStrategy.merge(bootstrapAutoDef)
    val mergedApp = mergeStrategy.merge(appAutoDef)

    validate(mergedBs, mergedApp)

    val bootstrapCustomDef = (Seq(new BootstrapModuleDef {
      make[LogRouter].from(loggerRouter)
    }) ++ strategy.bootstrapModules(mergedBs, mergedApp)).merge

    val bsdef = bootstrapCustomDef ++ mergedBs.definition
    val appDef = mergedApp.definition ++ strategy.appModules(mergedBs, mergedApp).merge

    logger.trace(s"Have bootstrap definition\n$bsdef")
    logger.trace(s"Have app definition\n$appDef")

    val injector = Injector(bsdef)
    val plan = injector.plan(appDef)
    logger.trace(s"Planning completed\n$plan")
    val context = injector.produce(plan)
    logger.trace("Context produced")
    start(context, strategy.context)
  }

  protected def validate(bootstrapAutoDef: LoadedPlugins, appDef: LoadedPlugins): Unit = {
    val conflicts = bootstrapAutoDef.definition.bindings.map(_.key).intersect(appDef.definition.bindings.map(_.key))
    if (conflicts.nonEmpty) {
      throw new DiAppBootstrapException(s"Same keys defined by bootstrap and app plugins: $conflicts. Most likely your bootstrap configs are contradictive, terminating...")
    }

    if (appDef.definition.bindings.isEmpty) {
      throw new DiAppBootstrapException("Empty app context. Most likely you have no plugins defined or your app plugin config is wrong, terminating...")
    }
  }

  protected def start(context: Locator, bootstrapContext: Strategy#Context): Unit

  def handler: AppFailureHandler = TerminatingHandler
}

class DiAppBootstrapException(message: String) extends DIException(message, null)
