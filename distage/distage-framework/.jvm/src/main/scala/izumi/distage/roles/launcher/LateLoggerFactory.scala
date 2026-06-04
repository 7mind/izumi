package izumi.distage.roles.launcher

import distage.{Id, Lifecycle}
import izumi.distage.roles.launcher.LoggerConfigLoader.DeclarativeLoggerConfig
import izumi.fundamentals.platform.functional.Identity
import izumi.logstage.adapter.jul.LogstageJulLogger
import izumi.logstage.api.logger.{LogQueue, LogRouter}
import izumi.logstage.api.routing.StaticLogRouter

import scala.util.chaining.scalaUtilChainingOps

trait LateLoggerFactory {
  def makeLateLogRouter(config: DeclarativeLoggerConfig): Lifecycle[Identity, LogRouter]
}

object LateLoggerFactory {
  class LateLoggerFactoryImpl(
    routerFactory: RouterFactory,
    buffer: LogQueue,
    setupStaticLogRouter: Boolean @Id("distage.roles.logs.static-log-router"),
  ) extends LateLoggerFactory {
    def makeLateLogRouter(config: DeclarativeLoggerConfig): Lifecycle[Identity, LogRouter] = {
      for {
        router <- Lifecycle.liftF[Identity, LogRouter] {
          val router = routerFactory.createRouter(config, buffer)
          if (setupStaticLogRouter) {
            StaticLogRouter.instance.setup(router)
          }
          router
        }
        _ <-
          if (config.interceptJUL) {
            Lifecycle.fromAutoCloseable(new LogstageJulLogger(router).tap(_.installOnly()))
          } else {
            Lifecycle.unit[Identity]
          }
      } yield {
        router
      }
    }
  }
}
