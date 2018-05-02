package com.github.pshirshov.izumi.logstage.distage

import com.github.pshirshov.izumi.distage.model.LoggerHook
import com.github.pshirshov.izumi.distage.model.definition.ModuleBuilder
import com.github.pshirshov.izumi.distage.model.planning.PlanningObserver
import com.github.pshirshov.izumi.logstage.api.IzLogger
import com.github.pshirshov.izumi.logstage.api.Log.CustomContext
import com.github.pshirshov.izumi.logstage.api.logger.LogRouter

class LogstageModule(router: LogRouter) extends ModuleBuilder {
  bind[LogRouter].as(router)
  bind(CustomContext.empty)
  bind[IzLogger]
  bind[PlanningObserver].as[PlanningObserverLoggingImpl]
  bind[LoggerHook].as[LoggerHookLoggingImpl]
}
