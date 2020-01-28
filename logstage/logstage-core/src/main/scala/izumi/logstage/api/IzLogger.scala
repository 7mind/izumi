package izumi.logstage.api

import izumi.logstage.api.Log.CustomContext
import izumi.logstage.api.logger.{AbstractMacroLogger, LogRouter, RoutingLogger}

class IzLogger
(
  override val router: LogRouter,
  override val customContext: Log.CustomContext
) extends RoutingLogger with AbstractMacroLogger {

  override def withCustomContext(context: CustomContext): IzLogger = new IzLogger(router, customContext + context)
  final def withCustomContext(context: (String, Any)*): IzLogger = withCustomContext(context.toMap)
  final def withCustomContext(context: Map[String, Any]): IzLogger = withCustomContext(CustomContext(context))

  final def apply(context: CustomContext): IzLogger = withCustomContext(context)
  final def apply(context: (String, Any)*): IzLogger = withCustomContext(context.toMap)
  final def apply(context: Map[String, Any]): IzLogger = withCustomContext(context)
}

object IzLogger extends IzLoggerConvenienceApi {
  override type Logger = IzLogger

  protected def make(r: LogRouter, context: CustomContext): Logger = {
    new IzLogger(r, context)
  }
}
