package izumi.distage.roles

import izumi.distage.config.model.AppConfig
import izumi.distage.model.definition.ModuleDef
import izumi.distage.roles.launcher.*
import izumi.logstage.api.logger.{LogQueue, LogRouter}
import izumi.logstage.api.{IzLogger, Log}
import logstage.ThreadingLogQueue

class RoleAppBootLoggerModule() extends ModuleDef {
  make[EarlyLoggerFactory].from[EarlyLoggerFactory.EarlyLoggerFactoryImpl]

  make[LoggerConfigLoader].from[LoggerConfigLoader.LogConfigLoaderImpl]
  make[RouterFactory].from[RouterFactory.RouterFactoryConsoleSinkImpl]
  make[LogQueue].fromResource(ThreadingLogQueue.resource())

  make[Log.Level].named("early").fromValue(Log.Level.Info)
  make[IzLogger].named("early").from {
    (factory: EarlyLoggerFactory, banner: StartupBanner) =>
      val logger = factory.makeEarlyLogger()
      banner.showBanner(logger)
      logger
  }

  make[LateLoggerFactory].from[LateLoggerFactory.LateLoggerFactoryImpl]
  make[LoggerConfigLoader.DeclarativeLoggerConfig].from {
    (loader: LoggerConfigLoader, config: AppConfig) =>
      loader.loadLoggingConfig(config)
  }
  make[LogRouter].fromResource {
    (factory: LateLoggerFactory, config: LoggerConfigLoader.DeclarativeLoggerConfig) =>
      factory.makeLateLogRouter(config)
  }
}
