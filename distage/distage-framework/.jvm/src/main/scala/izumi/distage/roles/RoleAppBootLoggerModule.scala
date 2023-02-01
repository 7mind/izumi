package izumi.distage.roles

import izumi.distage.model.definition.ModuleDef
import izumi.distage.modules.DefaultModule
import izumi.distage.roles.launcher.StartupBanner
import izumi.logstage.api.{IzLogger, Log}
import izumi.logstage.api.logger.LogRouter
import izumi.reflect.TagK
import izumi.distage.roles.launcher.*
import izumi.distage.roles.launcher.LateLoggerFactory.DistageAppLogging

class RoleAppBootLoggerModule[F[_]: TagK: DefaultModule]() extends ModuleDef {
  make[EarlyLoggerFactory].from[EarlyLoggerFactory.EarlyLoggerFactoryImpl]
  make[LateLoggerFactory].from[LateLoggerFactory.LateLoggerFactoryImpl]

  make[Log.Level].named("early").fromValue(Log.Level.Info)
  make[IzLogger].named("early").from {
    (factory: EarlyLoggerFactory, banner: StartupBanner) =>
      val logger = factory.makeEarlyLogger()
      banner.showBanner(logger)
      logger
  }
  make[DistageAppLogging].fromResource {
    (factory: LateLoggerFactory) =>
      factory.makeLateLogRouter()
  }
  make[LogRouter].from {
    (logging: DistageAppLogging) =>
      logging.router
  }

}


