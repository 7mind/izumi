package izumi.distage.roles.launcher

import distage.Lifecycle
import izumi.distage.roles.launcher.LoggerConfigLoader.DeclarativeLoggerConfig
import izumi.fundamentals.platform.functional.Identity
import izumi.logstage.api.logger.{LogQueue, LogRouter}
import izumi.logstage.api.routing.StaticLogRouter

trait LateLoggerFactory {
  def makeLateLogRouter(config: DeclarativeLoggerConfig): Lifecycle[Identity, LogRouter]
}

object LateLoggerFactory {
  class LateLoggerFactoryImpl(
    routerFactory: RouterFactory,
    buffer: LogQueue,
  ) extends LateLoggerFactory {
    def makeLateLogRouter(config: DeclarativeLoggerConfig): Lifecycle[Identity, LogRouter] = {
      Lifecycle.liftF[Identity, LogRouter] {
        val router = routerFactory.createRouter(config, buffer)
        StaticLogRouter.instance.setup(router)
        router
      }
    }
  }
}
