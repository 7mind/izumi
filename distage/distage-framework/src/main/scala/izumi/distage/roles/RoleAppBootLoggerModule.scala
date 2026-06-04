package izumi.distage.roles

import izumi.distage.config.model.AppConfig
import izumi.distage.model.definition.{Id, ModuleDef}
import izumi.distage.roles.launcher.*
import izumi.logstage.api.logger.{LogQueue, LogRouter}
import izumi.logstage.api.routing.StaticLogRouter
import izumi.logstage.api.{IzLogger, Log}
import logstage.ThreadingLogQueue

class RoleAppBootLoggerModule(
  crashLogRouterRef: Option[CrashLogRouterRef]
) extends ModuleDef {
  make[Option[CrashLogRouterRef]].fromValue(crashLogRouterRef)

  make[EarlyLoggerFactory].from[EarlyLoggerFactory.EarlyLoggerFactoryImpl]
  make[Log.Level].named("early").fromValue(Log.Level.Info)
  make[IzLogger].named("early").from {
    (
      factory: EarlyLoggerFactory,
      banner: StartupBanner,
      crashLogRouterRef: Option[CrashLogRouterRef],
      setupStaticLogRouter: Boolean @Id("distage.roles.logs.static-log-router"),
    ) =>
      val logger = factory.makeEarlyLogger()
      crashLogRouterRef.foreach(_.set(logger.router))
      if (setupStaticLogRouter) {
        StaticLogRouter.instance.setup(logger.router)
      }
      banner.showBanner(logger)
      logger
  }

  make[LoggerConfigLoader].from[LoggerConfigLoader.LogConfigLoaderImpl]
  make[RouterFactory].from[RouterFactory.RouterFactoryConsoleSinkImpl]
  make[LogQueue].fromResource(ThreadingLogQueue.resource())
  make[LateLoggerFactory].from[LateLoggerFactory.LateLoggerFactoryImpl]
  make[LoggerConfigLoader.DeclarativeLoggerConfig].from {
    (loader: LoggerConfigLoader, config: AppConfig) =>
      loader.loadLoggingConfig(config)
  }
  make[LogRouter].fromResource {
    (factory: LateLoggerFactory, config: LoggerConfigLoader.DeclarativeLoggerConfig, crashLogRouterRef: Option[CrashLogRouterRef]) =>
      factory.makeLateLogRouter(config).map {
        router =>
          crashLogRouterRef.foreach(_.set(router))
          router
      }
  }
}
