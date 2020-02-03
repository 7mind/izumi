package izumi.logstage.api

import izumi.logstage.api.Log.CustomContext
import izumi.logstage.api.logger.{AbstractMacroLogger, LogRouter, RoutingLogger}
import izumi.logstage.api.rendering.AnyEncoded

import scala.language.implicitConversions

class IzLogger
(
  override val router: LogRouter,
  override val customContext: Log.CustomContext
) extends RoutingLogger with AbstractMacroLogger {

  override def withCustomContext(context: CustomContext): IzLogger = new IzLogger(router, customContext + context)
  final def withCustomContext(context: (String, AnyEncoded)*): IzLogger = withCustomContextMap(context.toMap)
  final def withCustomContextMap(context: Map[String, AnyEncoded]): IzLogger = withCustomContext(CustomContext.fromMap(context))

  final def apply(context: CustomContext): IzLogger = withCustomContext(context)
  final def apply(context: (String, AnyEncoded)*): IzLogger = withCustomContextMap(context.toMap)
}

object IzLogger extends IzLoggerConvenienceApi {
  override type Logger = IzLogger

  protected def make(r: LogRouter, context: CustomContext): Logger = {
    new IzLogger(r, context)
  }
}




