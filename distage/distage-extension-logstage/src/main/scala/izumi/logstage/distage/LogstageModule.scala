package izumi.logstage.distage

import izumi.distage.model.definition.BootstrapModuleDef
import izumi.distage.model.planning.PlanningObserver
import izumi.logstage.api.IzLogger
import izumi.logstage.api.Log.CustomContext
import izumi.logstage.api.logger.{AbstractLogger, LogRouter, RoutingLogger}
import izumi.logstage.api.routing.StaticLogRouter

class LogstageModule(router: LogRouter, setupStaticLogRouter: Boolean) extends BootstrapModuleDef {
  if (setupStaticLogRouter) {
    StaticLogRouter.instance.setup(router)
  }

  make[IzLogger]
    .aliased[RoutingLogger]
    .aliased[AbstractLogger]

  make[LogRouter].fromValue(router)
  make[CustomContext].fromValue(CustomContext.empty)
}

object LogstageModule {
  @inline def apply(router: LogRouter, setupStaticLogRouter: Boolean): LogstageModule = new LogstageModule(router, setupStaticLogRouter)
}
