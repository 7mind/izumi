package com.github.pshirshov.izumi.distage.app

import com.github.pshirshov.izumi.distage.Injectors
import com.github.pshirshov.izumi.distage.config.model.AppConfig
import com.github.pshirshov.izumi.distage.model.Locator
import com.github.pshirshov.izumi.distage.model.definition.{ModuleBase, ModuleDef}
import com.github.pshirshov.izumi.distage.model.exceptions.DIException
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse
import com.github.pshirshov.izumi.distage.plugins._
import com.github.pshirshov.izumi.logstage.api.IzLogger
import com.github.pshirshov.izumi.logstage.api.Log.CustomContext
import com.github.pshirshov.izumi.logstage.api.logger.LogRouter

// TODO: startables
// TODO: config mapping/injection
// TODO: cli parser?..
// TODO: split into di-plugins and di-app


trait BootstrapContext[CommandlineConfig <: AnyRef] {
  def cliConfig: CommandlineConfig

  def bootstrapConfig: PluginConfig

  def pluginConfig: PluginConfig

  def appConfig: AppConfig
}

case class BootstrapContextDefaultImpl[CommandlineConfig <: AnyRef]
(
  cliConfig: CommandlineConfig
  , bootstrapConfig: PluginConfig
  , pluginConfig: PluginConfig
  , appConfig: AppConfig
) extends BootstrapContext[CommandlineConfig]

trait ApplicationBootstrapStrategy[CommandlineConfig <: AnyRef] {
  type Context = BootstrapContext[CommandlineConfig]

  def context: Context

  def requiredComponents: Set[RuntimeDIUniverse.DIKey]

  def mergeStrategy: PluginMergeStrategy[LoadedPlugins]

  def gc: DIGarbageCollector

  def router(): LogRouter

  def bootstrapModules(): Seq[ModuleBase]

  def mkBootstrapLoader(): PluginLoader

  def mkLoader(): PluginLoader
}


abstract class ApplicationBootstrapStrategyBaseImpl[CommandlineConfig <: AnyRef]
(
  override val context: BootstrapContext[CommandlineConfig]
) extends ApplicationBootstrapStrategy[CommandlineConfig] {
  def requiredComponents: Set[RuntimeDIUniverse.DIKey]

  def gc: DIGarbageCollector = TracingDIGC

  def mergeStrategy: PluginMergeStrategy[LoadedPlugins] = SimplePluginMergeStrategy

  def bootstrapModules(): Seq[ModuleBase] = Seq.empty

  def mkBootstrapLoader(): PluginLoader = new PluginLoaderDefaultImpl(context.bootstrapConfig)

  def mkLoader(): PluginLoader = new PluginLoaderDefaultImpl(context.pluginConfig)
}


abstract class OpinionatedDiApp {
  type CommandlineConfig <: AnyRef

  type Strategy = ApplicationBootstrapStrategy[CommandlineConfig]
  type BootstrapContext = Strategy#Context

  def main(args: Array[String]): Unit = {
    try {
      doMain(context(args))
    } catch {
      case t: Throwable =>
        handler.onError(t)
    }
  }

  protected def context(args: Array[String]): Strategy

  protected def doMain(strategy: Strategy): Unit = {
    val loggerRouter = strategy.router()

    val logger = new IzLogger(loggerRouter, CustomContext.empty) // TODO: add instance/machine id here?
    //val parsedArgs = argumentParser(args)
    val bootstrapLoader = strategy.mkBootstrapLoader()
    val appLoader = strategy.mkLoader()

    val bootstrapAutoDef = bootstrapLoader.loadDefinition(strategy.mergeStrategy)
    val appDef = appLoader.loadDefinition(strategy.mergeStrategy)

    validate(bootstrapAutoDef, appDef)

    val bootstrapCustomDef = (Seq(new ModuleDef {
      make[LogRouter].from(loggerRouter)
    }: ModuleBase) ++ strategy.bootstrapModules).merge

    val bsdef = bootstrapAutoDef.definition ++ bootstrapCustomDef

    logger.trace(s"Have bootstrap definition\n$bsdef")
    logger.trace(s"Have app definition\n$appDef")

    val injector = Injectors.bootstrap(bsdef)
    val plan = injector.plan(appDef.definition)
    logger.trace(s"Planning completed\n$plan")
    val refinedPlan = strategy.gc.gc(plan, DIGarbageCollector.isRoot(strategy.requiredComponents))
    logger.trace(s"Unrequired components disabled\n$refinedPlan")
    val context = injector.produce(refinedPlan)
    logger.trace(s"Context produced")
    start(context, strategy.context)
  }

  protected def validate(bootstrapAutoDef: LoadedPlugins, appDef: LoadedPlugins): Unit = {
    val conflicts = bootstrapAutoDef.definition.bindings.map(_.key).intersect(appDef.definition.bindings.map(_.key))
    if (conflicts.nonEmpty) {
      throw new DIException(s"Same keys defined by bootstrap and app plugins: $conflicts. Most likely your bootstrap configs are contradictive, terminating...", null)
    }

    if (appDef.definition.bindings.isEmpty) {
      throw new DIException(s"Empty app context. Most likely you have no plugins defined or your app plugin config is wrong, terminating...", null)
    }
  }

  protected def start(context: Locator, bootstrapContext: Strategy#Context): Unit

  def handler: AppFailureHandler = TerminatingHandler
}
