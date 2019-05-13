package com.github.pshirshov.izumi.logstage.distage

import com.github.pshirshov.izumi.distage.model.LoggerHook
import com.github.pshirshov.izumi.distage.model.definition.BootstrapModuleDef
import com.github.pshirshov.izumi.distage.model.planning.PlanningObserver
import com.github.pshirshov.izumi.logstage.api.IzLogger
import com.github.pshirshov.izumi.logstage.api.Log.CustomContext
import com.github.pshirshov.izumi.logstage.api.logger.LogRouter
import com.github.pshirshov.izumi.logstage.api.routing.StaticLogRouter

class LogstageModule(router: LogRouter, setupStatic: Boolean) extends BootstrapModuleDef {
  if (setupStatic) {
    StaticLogRouter.instance.setup(router)
  }

  make[LogRouter].fromValue(router)

  make[CustomContext].fromValue(CustomContext.empty)
  make[LoggerHook].from[LoggerHookLoggingImpl]
  many[PlanningObserver].add[PlanningObserverLoggingImpl]

  make[IzLogger]
}
