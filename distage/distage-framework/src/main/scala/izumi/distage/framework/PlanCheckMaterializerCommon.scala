package izumi.distage.framework

import izumi.distage.framework.model.PlanCheckResult
import izumi.distage.model.definition.ModuleBase
import izumi.distage.plugins.load.LoadedPlugins
import izumi.fundamentals.platform.console.TrivialLogger
import izumi.fundamentals.reflection.TypeUtil

private[framework] trait PlanCheckMaterializerCommon {

  /**
    * filter out anonymous classes that can't be referred in code
    * & retain only those that are suitable for being loaded by PluginLoader (objects / zero-arg classes) -
    * and that can be easily instantiated with `new`
    */
  protected def filterReferencablePlugins(checkedLoadedPlugins: LoadedPlugins): Seq[ModuleBase] = {
    checkedLoadedPlugins.allRaw
      .filterNot(TypeUtil `isAnonymous` _.getClass)
      .filter(p => TypeUtil.isObject(p.getClass).isDefined || TypeUtil.isZeroArgClass(p.getClass).isDefined)
  }

  protected def doCheckApp(
    roles: String,
    activations: String,
    config: String,
    checkConfig: Option[Boolean],
    printBindings: Option[Boolean],
    warn: Boolean,
    maybeMain: CheckableApp,
    logger: TrivialLogger,
  ): (Option[String], LoadedPlugins) = {
    PlanCheck.runtime.checkApp(
      app = maybeMain,
      cfg = new PlanCheckConfig(
        roles = roles,
        excludeActivations = activations,
        config = config,
        checkConfig = checkConfig.getOrElse(PlanCheck.defaultCheckConfig),
        printBindings = printBindings.getOrElse(PlanCheck.defaultPrintBindings),
        onlyWarn = warn,
      ),
      logger = logger,
    ) match {
      case PlanCheckResult.Correct(loadedPlugins, _) =>
        None -> loadedPlugins
      case PlanCheckResult.Incorrect(loadedPlugins, _, message, _) =>
        Some(message) -> loadedPlugins
    }
  }

}
