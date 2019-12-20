package izumi.distage.roles.logger

import com.typesafe.config.Config
import io.circe.Decoder
import izumi.distage.config.codec.{CirceDerivationConfigStyle, ConfigReader}
import izumi.distage.roles.logger.SimpleLoggerConfigurator.SinksConfig
import izumi.logstage.api.config.{LoggerConfig, LoggerPathConfig}
import izumi.logstage.api.logger.LogRouter
import izumi.logstage.api.rendering.json.LogstageCirceRenderingPolicy
import izumi.logstage.api.rendering.{RenderingOptions, StringRenderingPolicy}
import izumi.logstage.api.routing.{ConfigurableLogRouter, LogConfigServiceImpl, StaticLogRouter}
import izumi.logstage.api.{IzLogger, Log}
import izumi.logstage.sink.{ConsoleSink, QueueingSink}

import scala.util.{Failure, Success}

class SimpleLoggerConfigurator
(
  exceptionLogger: IzLogger,
) {

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
    SinksConfig.configReader(config.root) match {
      case Failure(exception) =>
        exceptionLogger.warn(s"Failed to read `logger` config section, using defaults: $exception")
        SinksConfig(Map.empty, None, json = None, None)

      case Success(value) =>
        value
    }
  }
}

object SimpleLoggerConfigurator {
  final case class SinksConfig(
                                levels: Map[String, List[String]],
                                options: Option[RenderingOptions],
                                json: Option[Boolean],
                                layout: Option[String],
                              )
  object SinksConfig {
    implicit val decoder: Decoder[SinksConfig] = CirceDerivationConfigStyle.deriveDecoder
    implicit val configReader: ConfigReader[SinksConfig] = ConfigReader.deriveFromCirce
  }
}
