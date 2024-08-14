package izumi.distage.roles.launcher

import izumi.distage.roles.launcher.LogConfigLoader.{DeclarativeLoggerConfig, LoggerFormat}
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
        case LoggerFormat.Json =>
          new LogstageCirceRenderingPolicy()
        case LoggerFormat.Text =>
          new StringRenderingPolicy(config.rendering, None)
      }
      val sink = new ConsoleSink(policy)
      val sinks = List(sink)

      val router = ConfigurableLogRouter(
        config.rootLevel,
        sinks,
        config.levels,
        buffer,
      )

      router
    }
  }
}
