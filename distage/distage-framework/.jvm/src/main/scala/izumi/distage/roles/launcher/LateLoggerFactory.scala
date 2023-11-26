package izumi.distage.roles.launcher

import distage.Lifecycle
import izumi.distage.roles.launcher.LogConfigLoader.DeclarativeLoggerConfig
import izumi.fundamentals.platform.functional.Identity
import izumi.logstage.adapter.jul.LogstageJulLogger
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
      for {
        router <- Lifecycle.liftF[Identity, LogRouter](routerFactory.createRouter(config, buffer))
        _ <- Lifecycle.make[Identity, Unit](StaticLogRouter.instance.setup(router))(_ => ())
        _ <- Lifecycle
          .make[Identity, Option[AutoCloseable]] {
            if (config.interceptJUL) {
              val julAdapter = new LogstageJulLogger(router)
              julAdapter.installOnly()
              Some(julAdapter)
            } else {
              None
            }
          }(_.foreach(_.close()))
      } yield {
        router
      }
    }
  }
}
