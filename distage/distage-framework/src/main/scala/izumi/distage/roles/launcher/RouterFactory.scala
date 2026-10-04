package izumi.distage.roles.launcher

import izumi.distage.roles.launcher.LoggerConfigLoader.{DeclarativeLoggerConfig, LoggerFormat}
import izumi.logstage.api.config.LoggingTarget
import izumi.logstage.api.logger.LogQueue
import izumi.logstage.api.rendering.StringRenderingPolicy
import logstage.circe.LogstageCirceRenderingPolicy
import logstage.{ConfigurableLogRouter, ConsoleSink}

trait RouterFactory {
  def createRouter(config: DeclarativeLoggerConfig, buffer: LogQueue): ConfigurableLogRouter
}

object RouterFactory {
  class RouterFactoryConsoleSinkImpl extends RouterFactory {
    override def createRouter(config: DeclarativeLoggerConfig, buffer: LogQueue): ConfigurableLogRouter = {
      val policy = config.format match {
        case LoggerFormat.Json => new LogstageCirceRenderingPolicy()
        case LoggerFormat.Text => new StringRenderingPolicy(config.rendering, None)
      }
      val sinks = List(new ConsoleSink(policy))
      val levels = config.levels.view.mapValues(level => LoggingTarget.Level(level)).toMap

      val router = ConfigurableLogRouter(
        rootThreshold = config.rootLevel,
        sinks = sinks,
        levels = levels,
        buffer = buffer,
      )

      router
    }
  }
}
