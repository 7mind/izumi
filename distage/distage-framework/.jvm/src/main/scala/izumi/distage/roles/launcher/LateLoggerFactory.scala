package izumi.distage.roles.launcher

import distage.Lifecycle
import izumi.distage.roles.launcher.LateLoggerFactory.DistageAppLogging
import izumi.distage.roles.launcher.LogConfigLoader.DeclarativeLoggerConfig
import izumi.fundamentals.platform.functional.Identity
import izumi.logstage.adapter.jul.LogstageJulLogger
import izumi.logstage.api.logger.LogRouter
import izumi.logstage.api.routing.StaticLogRouter
import logstage.{ConfigurableLogRouter, QueueingSink}

trait LateLoggerFactory {
  def makeLateLogRouter(config: DeclarativeLoggerConfig): Lifecycle[Identity, DistageAppLogging]
}

object LateLoggerFactory {
  case class DistageAppLogging(
    router: LogRouter,
    closeables: List[AutoCloseable],
  )

  class LateLoggerFactoryImpl(
    routerFactory: RouterFactory
  ) extends LateLoggerFactory {
    final def makeLateLogRouter(config: DeclarativeLoggerConfig): Lifecycle[Identity, DistageAppLogging] = {
      makeLateLogRouter(config, _.foreach(_.close()))
    }

    protected final def makeLateLogRouter(config: DeclarativeLoggerConfig, onClose: List[AutoCloseable] => Unit): Lifecycle[Identity, DistageAppLogging] = {
      for {
        router <- createThreadingRouter(config)
        out <- Lifecycle.make[Identity, DistageAppLogging] {
          StaticLogRouter.instance.setup(router)
          if (config.interceptJUL) {
            val julAdapter = new LogstageJulLogger(router)
            julAdapter.installOnly()
            DistageAppLogging(router, List(julAdapter, router))
          } else {
            DistageAppLogging(router, List(router))
          }
        }(loggers => onClose(loggers.closeables))
      } yield {
        out
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
