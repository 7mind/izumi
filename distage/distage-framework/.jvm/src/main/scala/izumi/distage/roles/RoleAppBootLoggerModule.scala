package izumi.distage.roles

import izumi.distage.config.model.AppConfig
import izumi.distage.model.definition.ModuleDef
import izumi.distage.modules.DefaultModule
import izumi.distage.roles.launcher.*
import izumi.logstage.api.logger.LogRouter
import izumi.logstage.api.{IzLogger, Log}
import izumi.reflect.TagK

class RoleAppBootLoggerModule[F[_]: TagK: DefaultModule]() extends ModuleDef {
  make[EarlyLoggerFactory].from[EarlyLoggerFactory.EarlyLoggerFactoryImpl]

  make[LogConfigLoader].from[LogConfigLoader.LogConfigLoaderImpl]
  make[RouterFactory].from[RouterFactory.RouterFactoryImpl]
  make[LateLoggerFactory].from[LateLoggerFactory.LateLoggerFactoryImpl]

  make[Log.Level].named("early").fromValue(Log.Level.Info)
  make[IzLogger].named("early").from {
    (factory: EarlyLoggerFactory, banner: StartupBanner) =>
      val logger = factory.makeEarlyLogger()
      banner.showBanner(logger)
      logger
  }
  make[LogRouter].fromResource {
    (factory: LateLoggerFactory, config: LogConfigLoader.DeclarativeLoggerConfig) =>
      factory.makeLateLogRouter(config)
  }
  make[LogConfigLoader.DeclarativeLoggerConfig].from {
    (loader: LogConfigLoader, config: AppConfig) =>
      loader.loadLoggingConfig(config)
  }
}
