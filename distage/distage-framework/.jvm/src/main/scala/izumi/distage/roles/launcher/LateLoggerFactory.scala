package izumi.distage.roles.launcher

import distage.Lifecycle
import izumi.distage.roles.launcher.LoggerConfigLoader.DeclarativeLoggerConfig
import izumi.functional.bio.Bifunctorized
import izumi.logstage.adapter.jul.LogstageJulLogger
import izumi.logstage.api.logger.{LogQueue, LogRouter}
import izumi.logstage.api.routing.StaticLogRouter

import scala.util.chaining.scalaUtilChainingOps

trait LateLoggerFactory {
  def makeLateLogRouter(config: DeclarativeLoggerConfig): Lifecycle[Bifunctorized.IdentityBifunctorized, Throwable, LogRouter]
}

object LateLoggerFactory {
  class LateLoggerFactoryImpl(
    routerFactory: RouterFactory,
    buffer: LogQueue,
  ) extends LateLoggerFactory {
    def makeLateLogRouter(config: DeclarativeLoggerConfig): Lifecycle[Bifunctorized.IdentityBifunctorized, Throwable, LogRouter] = {
      for {
        router <- Lifecycle.liftF[Bifunctorized.IdentityBifunctorized, Throwable, LogRouter] {
          val router = routerFactory.createRouter(config, buffer)
          StaticLogRouter.instance.setup(router)
          router
        }
        _ <-
          if (config.interceptJUL) {
            Lifecycle.fromAutoCloseable(new LogstageJulLogger(router).tap(_.installOnly()))
          } else {
            Lifecycle.unit[Bifunctorized.IdentityBifunctorized]
          }
      } yield {
        router
      }
    }
  }
}
