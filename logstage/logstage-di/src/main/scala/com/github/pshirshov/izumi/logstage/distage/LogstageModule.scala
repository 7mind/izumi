package com.github.pshirshov.izumi.logstage.distage

import com.github.pshirshov.izumi.distage.model.LoggerHook
import com.github.pshirshov.izumi.distage.model.definition.BootstrapModuleDef
import com.github.pshirshov.izumi.distage.model.planning.PlanningObserver
import com.github.pshirshov.izumi.logstage.api.IzLogger
import com.github.pshirshov.izumi.logstage.api.Log.CustomContext
import com.github.pshirshov.izumi.logstage.api.logger.LogRouter

class LogstageModule(router: LogRouter) extends BootstrapModuleDef {
  make[LogRouter].from(router)
  make[CustomContext].from(CustomContext.empty)
  make[IzLogger]
  make[LoggerHook].from[LoggerHookLoggingImpl]

  many[PlanningObserver].add[PlanningObserverLoggingImpl]
}
