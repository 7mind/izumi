package com.github.pshirshov.izumi.distage.app

import com.github.pshirshov.izumi.distage.model.{Locator, PlannerInput}
import com.github.pshirshov.izumi.distage.model.definition.{BootstrapModule, BootstrapModuleDef, ModuleBase}
import com.github.pshirshov.izumi.distage.model.plan.{CompactPlanFormatter, OrderedPlan}
import com.github.pshirshov.izumi.distage.plugins._
import com.github.pshirshov.izumi.distage.plugins.merge.PluginMergeStrategy
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks._
import com.github.pshirshov.izumi.logstage.api.IzLogger
import com.github.pshirshov.izumi.logstage.api.Log.CustomContext
import com.github.pshirshov.izumi.logstage.api.logger.LogRouter
import distage._

case class DIAppStartupContext(startupContext: StartupContext)

trait StartupContext {
  def logger: IzLogger
  def injector: Injector
  def plan: OrderedPlan
  def startup(args: Array[String]): StartupContext
}

abstract class OpinionatedDiApp {
  type CommandlineConfig

  def main(args: Array[String]): Unit = {
    try {
      val startupContext = startup(args)
      val context = makeContext(startupContext)
      start(context)
    } catch {
      case t: Throwable =>
        handler.onError(t)
    }
  }

  private def startup(args: Array[String]): StartupContext = {
    val config = commandlineSetup(args)
    val strategy = makeStrategy(config)
    new StartupContextImpl(strategy)
  }

  class StartupContextImpl(val strategy: ApplicationBootstrapStrategy) extends StartupContext {
    val loggerRouter: LogRouter = strategy.router()
    val logger: IzLogger = makeLogger(loggerRouter)
    val bsLoggerDef: BootstrapModuleDef = new BootstrapModuleDef {
      make[LogRouter].from(loggerRouter)
    }

    val pluginsBs: Seq[PluginBase] = strategy.mkBootstrapLoader().load()
    val pluginsApp: Seq[PluginBase] = strategy.mkLoader().load()

    val mergeStrategy: PluginMergeStrategy[LoadedPlugins] = strategy.mergeStrategy(pluginsBs, pluginsApp)

    val mergedBs: LoadedPlugins = mergeStrategy.merge(pluginsBs)
    val mergedApp: LoadedPlugins = mergeStrategy.merge(pluginsApp)

    validate(mergedBs, mergedApp)

    val appDef: ModuleBase = makeModule(strategy)(mergedBs, mergedApp)
    val bsModules: BootstrapModule = (Seq(bsLoggerDef) ++ strategy.bootstrapModules(mergedBs, mergedApp)).merge

    val accessibleBs: BootstrapModuleDef = new BootstrapModuleDef {
      make[DIAppStartupContext].from(DIAppStartupContext(StartupContextImpl.this))
    }

    val bootstrapCustomDef: BootstrapModule = bsModules ++ accessibleBs
    val bsdef: BootstrapModule = bootstrapCustomDef ++ mergedBs.definition

    logger.trace(s"Have bootstrap definition\n$bsdef")
    logger.trace(s"Have app definition\n$appDef")

    val injector: Injector = makeInjector(logger, bsdef)
    val plan: OrderedPlan = makePlan(logger, appDef, injector)

    override def startup(args: Array[String]): StartupContext = {
      OpinionatedDiApp.this.startup(args)
    }
  }

  protected def commandlineSetup(args: Array[String]): CommandlineConfig

  protected def makeStrategy(cliConfig: CommandlineConfig): ApplicationBootstrapStrategy

  private def makeModule(strategy: ApplicationBootstrapStrategy)(mergedBs: LoadedPlugins, mergedApp: LoadedPlugins): ModuleBase = {
    mergedApp.definition ++ strategy.appModules(mergedBs, mergedApp).merge
  }

  protected def makeLogger(loggerRouter: LogRouter): IzLogger = {
    val logger = new IzLogger(loggerRouter, CustomContext.empty) // TODO: add instance/machine id here?
    logger.info("Main logger initialized")
    logger
  }

  protected def makePlan(logger: IzLogger, appDef: ModuleBase, injector: distage.Injector): OrderedPlan = {
    val plan = injector.plan(PlannerInput(appDef))
    import CompactPlanFormatter._
    logger.debug(s"Planning completed\n${plan.render() -> "plan"}")
    plan
  }

  protected def makeContext(context: StartupContext): Locator = {
    val locator = context.injector.produceUnsafe(context.plan)
    context.logger.debug(s"Object graph produced with ${locator.instances.size -> "instances"}")
    locator
  }

  protected def makeInjector(logger: IzLogger, bsdef: BootstrapModule): distage.Injector = {
    logger.discard()
    Injector.Standard(bsdef)
  }

  protected def validate(bootstrapAutoDef: LoadedPlugins, appDef: LoadedPlugins): Unit = {
    val conflicts = bootstrapAutoDef.definition.keys.intersect(appDef.definition.keys)
    if (conflicts.nonEmpty) {
      throw new DiAppBootstrapException(s"Same keys defined by bootstrap and app plugins: $conflicts. Most likely your bootstrap configs are contradictive, terminating...")
    }

    if (appDef.definition.bindings.isEmpty) {
      throw new DiAppBootstrapException("Empty app object graph. Most likely you have no plugins defined or your app plugin config is wrong, terminating...")
    }
  }

  protected def start(context: Locator): Unit

  def handler: AppFailureHandler = AppFailureHandler.TerminatingHandler
}


