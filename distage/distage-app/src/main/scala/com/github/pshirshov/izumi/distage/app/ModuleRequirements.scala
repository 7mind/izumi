package com.github.pshirshov.izumi.distage.app

import com.github.pshirshov.izumi.distage.model.LoggerHook
import com.github.pshirshov.izumi.distage.model.planning.PlanningObserver
import com.github.pshirshov.izumi.logstage.api.IzLogger
import com.github.pshirshov.izumi.logstage.api.Log.CustomContext
import com.github.pshirshov.izumi.logstage.api.logger.LogRouter
import distage.DIKey

class ModuleRequirements(val keys: Set[DIKey])

class NoModuleRequirements extends ModuleRequirements(Set.empty)

class LogstageModuleRequirements extends ModuleRequirements(Set(
  DIKey.get[LogRouter]
, DIKey.get[CustomContext]
, DIKey.get[IzLogger]
, DIKey.get[PlanningObserver]
, DIKey.get[LoggerHook]
))
