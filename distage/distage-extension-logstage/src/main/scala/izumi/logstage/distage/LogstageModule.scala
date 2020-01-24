package izumi.logstage.distage

import izumi.distage.model.definition.BootstrapModuleDef
import izumi.distage.model.planning.PlanningObserver
import izumi.logstage.api.Log.CustomContext
import izumi.logstage.api.logger.{AbstractLogger, LogRouter, RoutingLogger}
import izumi.logstage.api.routing.StaticLogRouter
import izumi.logstage.api.IzLogger

class LogstageModule(router: LogRouter, setupStaticLogRouter: Boolean) extends BootstrapModuleDef {
  if (setupStaticLogRouter) {
    StaticLogRouter.instance.setup(router)
  }

  make[LogRouter].from(router)

  make[CustomContext].from(CustomContext.empty)
  many[PlanningObserver].add[PlanningObserverLoggingImpl]

  make[IzLogger]
  make[RoutingLogger].using[IzLogger]
  make[AbstractLogger].using[IzLogger]
}
