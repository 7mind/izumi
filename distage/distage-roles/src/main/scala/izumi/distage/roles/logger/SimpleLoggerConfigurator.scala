package izumi.distage.roles.logger

import com.typesafe.config.Config
import izumi.distage.model.reflection.universe.RuntimeDIUniverse.SafeType
import izumi.fundamentals.typesafe.config.{RuntimeConfigReaderCodecs, RuntimeConfigReaderDefaultImpl}
import izumi.logstage.api.config.{LoggerConfig, LoggerPathConfig}
import izumi.logstage.api.logger.LogRouter
import izumi.logstage.api.rendering.json.LogstageCirceRenderingPolicy
import izumi.logstage.api.rendering.{RenderingOptions, StringRenderingPolicy}
import izumi.logstage.api.routing.{ConfigurableLogRouter, LogConfigServiceImpl, StaticLogRouter}
import izumi.logstage.api.{IzLogger, Log}
import izumi.logstage.sink.{ConsoleSink, QueueingSink}

import scala.util.{Failure, Success, Try}

class SimpleLoggerConfigurator(
                                exceptionLogger: IzLogger,
                              ) {

  import SimpleLoggerConfigurator._

  // TODO: this is a temporary solution until we finish full-scale logger configuration support
  def makeLogRouter(config: Config, root: Log.Level, json: Boolean): LogRouter = {
    val logconf = readConfig(config)

    val renderingPolicy = if (logconf.json.contains(true) || json) {
      new LogstageCirceRenderingPolicy()
    } else {
      val options = logconf.options.getOrElse(RenderingOptions())
      new StringRenderingPolicy(options)
    }

    val queueingSink = new QueueingSink(new ConsoleSink(renderingPolicy))
    val sinks = Seq(queueingSink)

    val levels = logconf.levels.flatMap {
      case (stringLevel, pack) =>
        val level = Log.Level.parseLetter(stringLevel)
        pack.map((_, LoggerPathConfig(level, sinks)))
    }

    // TODO: here we may read log configuration from config file
    val result = new ConfigurableLogRouter(
      new LogConfigServiceImpl(
        LoggerConfig(LoggerPathConfig(root, sinks), levels)
      )
    )
    queueingSink.start()
    StaticLogRouter.instance.setup(result)
    result
  }

  private[this] def readConfig(config: Config): SinksConfig = {
    // TODO: copypaste from di boostrap, this MUST disappear
    val reader = new RuntimeConfigReaderDefaultImpl(RuntimeConfigReaderCodecs.default.readerCodecs)

    val maybeConf = for {
      section <- Try(config)
      config <- Try(reader.readConfigAsCaseClass(section, SafeType.get[SinksConfig]).asInstanceOf[SinksConfig])
    } yield config

    maybeConf match {
      case Failure(exception) =>
        exceptionLogger.error(s"Failed to read `logger` config section, using defaults: $exception")
        SinksConfig(Map.empty, None, json = None, None)

      case Success(value) =>
        value
    }
  }
}

object SimpleLoggerConfigurator {
  final case class SinksConfig(levels: Map[String, List[String]], options: Option[RenderingOptions], json: Option[Boolean], layout: Option[String])
}
