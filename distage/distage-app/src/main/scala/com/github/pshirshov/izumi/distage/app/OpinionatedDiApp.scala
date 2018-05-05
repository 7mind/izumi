package com.github.pshirshov.izumi.distage.app

import com.github.pshirshov.izumi.distage.Injectors
import com.github.pshirshov.izumi.distage.model.Locator
import com.github.pshirshov.izumi.distage.model.definition.TrivialModuleDef
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse
import com.github.pshirshov.izumi.distage.plugins._
import com.github.pshirshov.izumi.logstage.api.IzLogger
import com.github.pshirshov.izumi.logstage.api.Log.CustomContext
import com.github.pshirshov.izumi.logstage.api.logger.LogRouter

// TODO: startables
// TODO: config mapping/injection
// TODO: cli parser?..
// TODO: split into di-plugins and di-app
abstract class OpinionatedDiApp {
  def main(args: Array[String]): Unit = {
    try {
      doMain()
    } catch {
      case t: Throwable =>
        handler.onError(t)
    }
  }

  protected def doMain(): Unit = {
    val logger = new IzLogger(router, CustomContext.empty) // TODO: add instance/machine id here?
    val bootstrapLoader = mkBootstrapLoader(bootstrapConfig)
    val appLoader = mkLoader(appConfig)

    val bootstrapAutoDef = bootstrapLoader.loadDefinition(mergeStrategy)
    val bootstrapCustomDef = TrivialModuleDef.bind[LogRouter](router)
    val appDef = appLoader.loadDefinition(mergeStrategy)
    logger.trace(s"Have bootstrap definition\n$appDef")
    logger.trace(s"Have app definition\n$appDef")

    val injector = Injectors.bootstrap(bootstrapAutoDef.definition ++ bootstrapCustomDef)
    val plan = injector.plan(appDef.definition)
    logger.trace(s"Planning completed\n$plan")
    val refinedPlan = gc.gc(plan, DIGarbageCollector.isRoot(requiredComponents))
    logger.trace(s"Unrequired components disabled\n$refinedPlan")
    val context = injector.produce(refinedPlan)
    logger.trace(s"Context produced")
    start(context)
  }

  protected def start(context: Locator): Unit

  protected def bootstrapConfig: PluginConfig

  protected val appConfig: PluginConfig

  protected def router: LogRouter

  protected def requiredComponents: Set[RuntimeDIUniverse.DIKey]

  // sane defaults
  protected def gc: DIGarbageCollector = TracingDIGC

  protected def mergeStrategy: PluginMergeStrategy[LoadedPlugins] = SimplePluginMergeStrategy

  protected def handler: AppFailureHandler = TerminatingHandler

  protected def mkBootstrapLoader(config: PluginConfig): PluginLoader = new PluginLoaderDefaultImpl(config)

  protected def mkLoader(config: PluginConfig): PluginLoader = new PluginLoaderDefaultImpl(config)
}
