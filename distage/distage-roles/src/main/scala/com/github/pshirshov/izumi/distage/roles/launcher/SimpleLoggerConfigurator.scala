package com.github.pshirshov.izumi.distage.roles.launcher

import com.github.pshirshov.izumi.fundamentals.typesafe.config.{RuntimeConfigReaderCodecs, RuntimeConfigReaderDefaultImpl}
import com.github.pshirshov.izumi.logstage.api.config.{LoggerConfig, LoggerPathConfig}
import com.github.pshirshov.izumi.logstage.api.logger.LogRouter
import com.github.pshirshov.izumi.logstage.api.rendering.json.LogstageCirceRenderingPolicy
import com.github.pshirshov.izumi.logstage.api.rendering.{RenderingOptions, StringRenderingPolicy}
import com.github.pshirshov.izumi.logstage.api.routing.{ConfigurableLogRouter, LogConfigServiceImpl, StaticLogRouter}
import com.github.pshirshov.izumi.logstage.api.{IzLogger, Log}
import com.github.pshirshov.izumi.logstage.sink.{ConsoleSink, QueueingSink}
import com.typesafe.config.Config

import scala.util.{Failure, Success, Try}

class SimpleLoggerConfigurator(logger: IzLogger) {

  import SimpleLoggerConfigurator._

  // TODO: this is a temporary solution until we finish full-scale logger configuration support
  def makeLogRouter(config: Config, root: Log.Level, json: Boolean): LogRouter = {

    val logconf = readConfig(config)

    val renderingPolicy = if (logconf.json.contains(true) || json) {
      new LogstageCirceRenderingPolicy()
    } else {
      val options = logconf.options match {
        case Some(value) =>
          value
        case None =>
          RenderingOptions()
      }
      new StringRenderingPolicy(options, logconf.layout)
    }

    val queueingSink = new QueueingSink(new ConsoleSink(renderingPolicy))
    val sinks = Seq(queueingSink)

    val levels = logconf.levels.flatMap {
      case (stringLevel, pack) =>
        val level = IzLogger.parseLevel(stringLevel)
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

  private def readConfig(config: Config) = {
    // TODO: copypaste from di boostrap, this MUST disappear
    import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._
    val reader = new RuntimeConfigReaderDefaultImpl(RuntimeConfigReaderCodecs.default.readerCodecs)

    val maybeConf = for {
      section <- Try(config)
      config <- Try(reader.readConfigAsCaseClass(section, SafeType.get[SinksConfig]).asInstanceOf[SinksConfig])
    } yield {
      config
    }

    val logconf = maybeConf match {
      case Failure(exception) =>
        logger.error(s"Failed to read `logger` config section, using defaults: $exception")
        SinksConfig(Map.empty, None, json = None, None)

      case Success(value) =>
        value
    }
    logconf
  }
}

object SimpleLoggerConfigurator {

  case class SinksConfig(levels: Map[String, List[String]], options: Option[RenderingOptions], json: Option[Boolean], layout: Option[String])

}
