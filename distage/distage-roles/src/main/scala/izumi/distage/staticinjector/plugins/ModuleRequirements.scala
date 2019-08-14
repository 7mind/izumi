package izumi.distage.staticinjector.plugins

import izumi.distage.model.LoggerHook
import izumi.distage.model.planning.PlanningObserver
import izumi.logstage.api.IzLogger
import izumi.logstage.api.Log.CustomContext
import izumi.logstage.api.logger.LogRouter
import distage.DIKey

class ModuleRequirements(val requiredKeys: Set[DIKey])

class NoModuleRequirements extends ModuleRequirements(Set.empty)

class LogstageModuleRequirements extends ModuleRequirements(Set(
  DIKey.get[LogRouter]
, DIKey.get[CustomContext]
, DIKey.get[IzLogger]
, DIKey.get[Set[PlanningObserver]]
, DIKey.get[LoggerHook]
))
