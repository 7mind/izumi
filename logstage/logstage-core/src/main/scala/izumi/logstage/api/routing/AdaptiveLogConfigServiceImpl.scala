package izumi.logstage.api.routing

import izumi.logstage.api.Log
import izumi.logstage.api.config.{LogEntryConfig, LoggerConfig}
import izumi.logstage.api.logger.LogSink

final class AdaptiveLogConfigServiceImpl(
  loggerConfig: LoggerConfig,
  sinksRoutes: Map[String, Seq[LogSink]],
) extends LogConfigServiceImpl(loggerConfig) {

  override def config(e: Log.Entry): LogEntryConfig = {
    val baseConfig = super.config(e)
    e.context.sinkRouteKey.flatMap(sinksRoutes.get) match {
      case None =>
        baseConfig
      case Some(routeSinks) =>
        LogEntryConfig(routeSinks.toSet.intersect(baseConfig.sinks.toSet).toSeq)
    }
  }
}