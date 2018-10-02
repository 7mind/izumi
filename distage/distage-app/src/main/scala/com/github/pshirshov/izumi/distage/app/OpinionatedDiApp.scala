package com.github.pshirshov.izumi.distage.app

import com.github.pshirshov.izumi.distage.model.Locator
import com.github.pshirshov.izumi.distage.model.definition.{BootstrapModule, BootstrapModuleDef, ModuleBase}
import com.github.pshirshov.izumi.distage.model.plan.{OrderedPlan, PlanFormatter}
import com.github.pshirshov.izumi.distage.plugins._
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks._
import com.github.pshirshov.izumi.logstage.api.IzLogger
import com.github.pshirshov.izumi.logstage.api.Log.CustomContext
import com.github.pshirshov.izumi.logstage.api.logger.LogRouter
import distage.Injector


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
    val bootstrapLoader = strategy.mkBootstrapLoader()
    val appLoader = strategy.mkLoader()

    val bootstrapAutoDef = bootstrapLoader.load()
    val appAutoDef = appLoader.load()
    val mergeStrategy = strategy.mergeStrategy(bootstrapAutoDef, appAutoDef)

    val loggerRouter = strategy.router()
    val logger: IzLogger = makeLogger(loggerRouter)

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

    val injector = makeInjector(logger, bsdef)
    val plan = makePlan(logger, appDef, injector)
    val context = makeContext(logger, injector, plan)

    start(context, strategy.context)
  }

  protected def makeLogger(loggerRouter: LogRouter): IzLogger = {
    val logger = new IzLogger(loggerRouter, CustomContext.empty) // TODO: add instance/machine id here?
    logger.info("Main logger initialized")
    logger
  }

  protected def makeContext(logger: IzLogger, injector: distage.Injector, plan: OrderedPlan): Locator = {
    val locator = injector.produce(plan)
    logger.trace(s"Context produced with ${locator.instances.size -> "instances"}")
    locator
  }

  protected def makePlan(logger: IzLogger, appDef: ModuleBase, injector: distage.Injector): OrderedPlan = {
    val plan = injector.plan(appDef)
    val formatter = new PlanFormatter()
    logger.trace(s"Planning completed\n${formatter.format(plan)}")
    plan
  }

  protected def makeInjector(logger: IzLogger, bsdef: BootstrapModule): distage.Injector = {
    logger.discard()
    Injector(bsdef)
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

  def handler: AppFailureHandler = AppFailureHandler.TerminatingHandler
}


