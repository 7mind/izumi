package izumi.logstage.api.strict

import izumi.logstage.api.Log.CustomContext
import izumi.logstage.api.logger.{AbstractMacroStrictLogger, LogRouter, RoutingLogger}
import izumi.logstage.api.{IzLogger, IzLoggerConvenienceApi, Log}


class IzStrictLogger
(
  override val router: LogRouter
, override val customContext: Log.CustomContext
) extends RoutingLogger with AbstractMacroStrictLogger {

  override def withCustomContext(context: CustomContext): IzLogger = new IzLogger(router, customContext + context)
  final def withCustomContext(context: (String, Any)*): IzLogger = withCustomContext(context.toMap)
  final def withCustomContext(context: Map[String, Any]): IzLogger = withCustomContext(CustomContext(context))

  final def apply(context: CustomContext): IzLogger = withCustomContext(context)
  final def apply(context: (String, Any)*): IzLogger = withCustomContext(context.toMap)
  final def apply(context: Map[String, Any]): IzLogger = withCustomContext(context)

}

object IzStrictLogger extends IzLoggerConvenienceApi {
  override type Logger = IzStrictLogger

  override protected def make(r: LogRouter, context: CustomContext): IzStrictLogger = {
    new IzStrictLogger(r, context)
  }
}