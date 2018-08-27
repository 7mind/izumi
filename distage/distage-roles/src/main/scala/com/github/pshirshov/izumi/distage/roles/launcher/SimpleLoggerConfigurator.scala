package com.github.pshirshov.izumi.distage.roles.launcher

import com.github.pshirshov.izumi.distage.config.codec.RuntimeConfigReaderDefaultImpl
import com.github.pshirshov.izumi.distage.reflection
import com.github.pshirshov.izumi.logstage.api.config.LoggerConfig
import com.github.pshirshov.izumi.logstage.api.logger.LogRouter
import com.github.pshirshov.izumi.logstage.api.rendering.json.LogstageCirceRenderingPolicy
import com.github.pshirshov.izumi.logstage.api.rendering.{RenderingOptions, StringRenderingPolicy}
import com.github.pshirshov.izumi.logstage.api.routing.{ConfigurableLogRouter, LogConfigServiceStaticImpl, StaticLogRouter}
import com.github.pshirshov.izumi.logstage.api.{IzLogger, Log}
import com.github.pshirshov.izumi.logstage.sink.ConsoleSink
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

    val sinks = Seq(new ConsoleSink(renderingPolicy))

    val levels = logconf.levels.flatMap {
      case (stringLevel, pack) =>
        val level = IzLogger.parseLevel(stringLevel)
        pack.map((_, LoggerConfig(level, sinks)))
    }

    // TODO: here we may read log configuration from config file
    val result = new ConfigurableLogRouter(
      new LogConfigServiceStaticImpl(
        levels
        , LoggerConfig(root, sinks)
      )
    )
    StaticLogRouter.instance.setup(result)
    result
  }

  private def readConfig(config: Config) = {
    // TODO: copypaste from di boostrap, this MUST disappear
    import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._
    val symbolIntrospector = new reflection.SymbolIntrospectorDefaultImpl.Runtime
    val reflectionProvider = new reflection.ReflectionProviderDefaultImpl.Runtime(
      new reflection.DependencyKeyProviderDefaultImpl.Runtime(symbolIntrospector)
      , symbolIntrospector
    )
    val reader = new RuntimeConfigReaderDefaultImpl(reflectionProvider, symbolIntrospector)


    val maybeConf = for {
      section <- Try(config)
      config <- Try(reader.readConfig(section, SafeType.get[SinksConfig]).asInstanceOf[SinksConfig])
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
