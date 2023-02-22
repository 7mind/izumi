package izumi.distage.roles.launcher

import izumi.distage.roles.launcher.LogConfigLoader.{DeclarativeLoggerConfig, LoggerFormat}
import izumi.logstage.api.config.{LoggerConfig, LoggerPathConfig}
import izumi.logstage.api.logger.LogSink
import izumi.logstage.api.rendering.StringRenderingPolicy
import izumi.logstage.api.routing.LogConfigServiceImpl
import logstage.circe.LogstageCirceRenderingPolicy
import logstage.{ConfigurableLogRouter, ConsoleSink}

trait RouterFactory {
  def createRouter[A <: LogSink](config: DeclarativeLoggerConfig)(wrap: ConsoleSink => A): (ConfigurableLogRouter, A)
}

object RouterFactory {
  class RouterFactoryImpl extends RouterFactory {
    override def createRouter[A <: LogSink](config: DeclarativeLoggerConfig)(wrap: ConsoleSink => A): (ConfigurableLogRouter, A) = {
      val policy = config.format match {
        case LoggerFormat.Json =>
          new LogstageCirceRenderingPolicy()
        case LoggerFormat.Text =>
          new StringRenderingPolicy(config.rendering, None)
      }
      val sink = wrap(new ConsoleSink(policy))
      val sinks = List(sink)
      val levelConfigs = config.levels.map {
        case (pkg, level) =>
          (pkg, LoggerPathConfig(level, sinks))
      }

      // TODO: here we may read log configuration from config file
      val router = new ConfigurableLogRouter(
        new LogConfigServiceImpl(
          LoggerConfig(LoggerPathConfig(config.rootLevel, sinks), levelConfigs)
        )
      )

      (router, sink)
    }
  }
}
