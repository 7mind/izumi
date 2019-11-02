package izumi.logstage.distage

import izumi.distage.model.LoggerHook
import izumi.distage.model.definition.BootstrapModuleDef
import izumi.distage.model.planning.PlanningObserver
import izumi.logstage.api.{AbstractLogger, IzLogger, RoutingLogger}
import izumi.logstage.api.Log.CustomContext
import izumi.logstage.api.logger.LogRouter
import izumi.logstage.api.routing.StaticLogRouter

class LogstageModule(router: LogRouter, setupStatic: Boolean) extends BootstrapModuleDef {
  if (setupStatic) {
    StaticLogRouter.instance.setup(router)
  }

  make[LogRouter].from(router)

  make[CustomContext].from(CustomContext.empty)
  make[LoggerHook].from[LoggerHookLoggingImpl]
  many[PlanningObserver].add[PlanningObserverLoggingImpl]

  make[IzLogger]
  make[RoutingLogger].using[IzLogger]
  make[AbstractLogger].using[IzLogger]
}
