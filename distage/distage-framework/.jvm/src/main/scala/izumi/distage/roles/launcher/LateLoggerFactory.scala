package izumi.distage.roles.launcher

import distage.Lifecycle
import izumi.distage.roles.launcher.LogConfigLoader.DeclarativeLoggerConfig
import izumi.fundamentals.platform.functional.Identity
import izumi.logstage.adapter.jul.LogstageJulLogger
import izumi.logstage.api.logger.LogRouter
import izumi.logstage.api.routing.StaticLogRouter
import logstage.{ConfigurableLogRouter, QueueingSink}

trait LateLoggerFactory {
  def makeLateLogRouter(config: DeclarativeLoggerConfig): Lifecycle[Identity, LogRouter]
}

object LateLoggerFactory {
  class LateLoggerFactoryImpl(
    routerFactory: RouterFactory
  ) extends LateLoggerFactory {
    def makeLateLogRouter(config: DeclarativeLoggerConfig): Lifecycle[Identity, LogRouter] = {
      for {
        router <- createThreadingRouter(config)
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

    protected final def createThreadingRouter(config: DeclarativeLoggerConfig): Lifecycle[Identity, ConfigurableLogRouter] = {
      Lifecycle
        .make[Identity, (ConfigurableLogRouter, QueueingSink)] {
          val (router, queueingSink) = routerFactory.createRouter(config)(sink => new QueueingSink(sink))
          queueingSink.start()
          (router, queueingSink)
        } {
          case (_, sink) => sink.close()
        }.map(_._1)
    }

  }
}
