package izumi.distage.roles.launcher

import com.typesafe.config.Config
import izumi.distage.config.codec.DIConfigReader
import izumi.distage.roles.launcher.SimpleLoggerConfigurator.SinksConfig
import izumi.logstage.api.Log.Level.Warn
import izumi.logstage.api.Log.Message
import izumi.logstage.api.config.{LoggerConfig, LoggerPathConfig}
import izumi.logstage.api.logger.LogRouter
import izumi.logstage.api.rendering.json.LogstageCirceRenderingPolicy
import izumi.logstage.api.rendering.{RenderingOptions, StringRenderingPolicy}
import izumi.logstage.api.routing.{ConfigurableLogRouter, LogConfigServiceImpl, StaticLogRouter}
import izumi.logstage.api.{IzLogger, Log}
import izumi.logstage.sink.{ConsoleSink, QueueingSink}

import scala.util.Try

class SimpleLoggerConfigurator(
  exceptionLogger: IzLogger
) {

  // TODO: this is a temporary solution until we finish full-scale logger configuration support
  def makeLogRouter(config: Config, root: Log.Level, json: Boolean): LogRouter = {
    val logconf = readConfig(config)

    val renderingPolicy = if (logconf.json.contains(true) || json) {
      new LogstageCirceRenderingPolicy()
    } else {
      val options = logconf.options.getOrElse(RenderingOptions.default)
      new StringRenderingPolicy(options, None)
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
    Try(config.getConfig("logger"))
      .toEither
      .left.map(_ => Message("No `logger` section in config. Using defaults."))
      .flatMap {
        config =>
          SinksConfig.configReader.decodeConfigValue(config.root).toEither.left.map {
            exception =>
              Message(s"Failed to parse `logger` config section into ${classOf[SinksConfig] -> "type"}. Using defaults. $exception")
          }
      } match {
      case Left(errMessage) =>
        exceptionLogger.log(Warn)(errMessage)
        SinksConfig(Map.empty, None, None, None)

      case Right(value) =>
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
    implicit val configReader: DIConfigReader[SinksConfig] = DIConfigReader.derived
  }
}
