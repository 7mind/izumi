package com.github.pshirshov.izumi.distage.app

import com.github.pshirshov.izumi.distage.Injectors
import com.github.pshirshov.izumi.distage.model.Locator
import com.github.pshirshov.izumi.distage.model.definition.{ModuleBase, ModuleDef}
import com.github.pshirshov.izumi.distage.model.exceptions.DIException
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse
import com.github.pshirshov.izumi.distage.plugins._
import com.github.pshirshov.izumi.logstage.api.IzLogger
import com.github.pshirshov.izumi.logstage.api.Log.CustomContext
import com.github.pshirshov.izumi.logstage.api.logger.LogRouter
import com.github.pshirshov.izumi.logstage.api.rendering.RenderingPolicy

// TODO: startables
// TODO: config mapping/injection
// TODO: cli parser?..
// TODO: split into di-plugins and di-app
abstract class OpinionatedDiApp {
  def main(args: Array[String]): Unit = {
    try {
      doMain(args)
    } catch {
      case t: Throwable =>
        handler.onError(t)
    }
  }

  protected def doMain(args : Array[String]): Unit = {
    val logger = new IzLogger(router, CustomContext.empty) // TODO: add instance/machine id here?
    val bootstrapLoader = mkBootstrapLoader(bootstrapConfig)
    val appLoader = mkLoader(appConfig)

    val bootstrapAutoDef = bootstrapLoader.loadDefinition(mergeStrategy)
    val appDef = appLoader.loadDefinition(mergeStrategy)

    validate(bootstrapAutoDef, appDef)

    val bootstrapCustomDef = (Seq(new ModuleDef {
      make[LogRouter].from(router)
    } : ModuleBase) ++ bootstrapModules).merge

    val bsdef = bootstrapAutoDef.definition ++ bootstrapCustomDef

    logger.trace(s"Have bootstrap definition\n$bsdef")
    logger.trace(s"Have app definition\n$appDef")

    val injector = Injectors.bootstrap(bsdef)
    val plan = injector.plan(appDef.definition)
    logger.trace(s"Planning completed\n$plan")
    val refinedPlan = gc.gc(plan, DIGarbageCollector.isRoot(requiredComponents))
    logger.trace(s"Unrequired components disabled\n$refinedPlan")
    val context = injector.produce(refinedPlan)
    logger.trace(s"Context produced")
    start(context, args)
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

  protected def start(context: Locator, args: Array[String]): Unit

  protected def bootstrapConfig: PluginConfig

  protected val appConfig: PluginConfig

  protected def router: LogRouter

  protected def bootstrapModules: Seq[ModuleBase] = Seq.empty

  protected def requiredComponents: Set[RuntimeDIUniverse.DIKey]

  // sane defaults
  protected def gc: DIGarbageCollector = TracingDIGC

  protected def mergeStrategy: PluginMergeStrategy[LoadedPlugins] = SimplePluginMergeStrategy

  protected def handler: AppFailureHandler = TerminatingHandler

  protected def mkBootstrapLoader(config: PluginConfig): PluginLoader = new PluginLoaderDefaultImpl(config)

  protected def mkLoader(config: PluginConfig): PluginLoader = new PluginLoaderDefaultImpl(config)
}
