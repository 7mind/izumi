package izumi.logstage.api.routing

import izumi.fundamentals.collections.nonempty.NEList
import izumi.fundamentals.platform.console.TrivialLogger
import izumi.fundamentals.platform.console.TrivialLogger.Config
import izumi.fundamentals.platform.language.CodePosition
import izumi.logstage.DebugProperties
import izumi.logstage.api.Log
import izumi.logstage.api.config.*
import izumi.logstage.api.logger.{LogQueue, LogRouter, LogSink}
import izumi.logstage.sink.{ConsoleSink, FallbackConsoleSink}

import scala.annotation.nowarn
import scala.util.control.NonFatal

class ConfigurableLogRouter(
  logConfigService: LogConfigService,
  buffer: LogQueue,
) extends LogRouter {

  override def log(entry: Log.Entry): Unit = {
    val sinks = logConfigService
      .config(entry)
      .sinks

    sinks.foreach {
      sink =>
        try {
          buffer.append(entry, sink)
        } catch {
          case NonFatal(e) =>
            ConfigurableLogRouter.fallback.log(s"Log sink $sink failed", e)
        }
    }
  }

  override def acceptable(id: Log.LoggerId, messageLevel: Log.Level): Boolean = {
    logConfigService.acceptable(id, messageLevel)
  }

  override def toString: String = s"${super.toString} with `$buffer` queue and configured with $logConfigService"

  override def acceptable(position: CodePosition, logLevel: Log.Level): Boolean = {
    logConfigService.acceptable(position, logLevel)
  }
}

object ConfigurableLogRouter {
  private final val fallback: TrivialLogger = TrivialLogger.make[FallbackConsoleSink](DebugProperties.`izumi.logstage.routing.log-failures`.name, Config(forceLog = true))

  def apply(
    rootThreshold: Log.Level = Log.Level.Trace,
    sink: LogSink = ConsoleSink.ColoredConsoleSink,
    levels: Map[String, LoggingTarget] = Map.empty,
    buffer: LogQueue = LogQueue.Immediate,
  ): ConfigurableLogRouter = {
    ConfigurableLogRouter(rootThreshold, Seq(sink), levels, buffer)
  }

  def apply(rootThreshold: Log.Level, sinks: Seq[LogSink], buffer: LogQueue): ConfigurableLogRouter = {
    ConfigurableLogRouter(rootThreshold, sinks, Map.empty[String, LoggingTarget], buffer)
  }

  def apply(logConfigService: LogConfigService, buffer: LogQueue): ConfigurableLogRouter = {
    new ConfigurableLogRouter(logConfigService, buffer)
  }

  @nowarn("msg=Unused import")
  def apply(rootThreshold: Log.Level, sinks: Seq[LogSink], levels: Map[String, LoggingTarget], buffer: LogQueue): ConfigurableLogRouter = {
    import izumi.fundamentals.collections.IzCollections.*

    import scala.collection.compat.*

    def toConfig(target: LoggingTarget) = {
      target match {
        case LoggingTarget.Level(level) => LoggerPathConfig(level, sinks)
        case LoggingTarget.Config(config) => config
      }
    }

    val levelConfigs = levels.view
      .flatMap {
        case (id, lvl) =>
          LoggerPath.parse(id).map(rule => (rule, lvl)).toList
      }.toMultimapView.map {
        case (path, levels) =>
          LoggerRule(path, toConfig(levels.minBy(_.level)))
      }.toList

    val rootRule = LoggerRule(LoggerPath(NEList(LoggerPathElement.Wildcard), Set.empty), LoggerPathConfig(rootThreshold, sinks))

    val configService = new LogConfigServiceImpl(LoggerConfig(levelConfigs, rootRule))
    configService.validate(fallback)

    ConfigurableLogRouter(configService, buffer)
  }

}
