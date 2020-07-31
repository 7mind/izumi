package izumi.logstage.api

import izumi.logstage.api.Log.CustomContext
import izumi.logstage.api.logger.{AbstractLogger, AbstractMacroLogger, AbstractMacroRawLogger, LogRouter, RoutingLogger}
import izumi.logstage.api.rendering.AnyEncoded

class IzLogger(
  override val router: LogRouter,
  override val customContext: Log.CustomContext,
) extends RoutingLogger
  with AbstractMacroLogger { self =>

  override type Self = IzLogger

  override def withCustomContext(context: CustomContext): Self = new IzLogger(router, customContext + context)
  final def withCustomContext(context: (String, AnyEncoded)*): Self = withCustomContextMap(context.toMap)
  final def withCustomContextMap(context: Map[String, AnyEncoded]): Self = withCustomContext(CustomContext.fromMap(context))

  final def apply(context: CustomContext): Self = withCustomContext(context)
  final def apply(context: (String, AnyEncoded)*): Self = withCustomContextMap(context.toMap)

  final def raw: IzRawLogger = new IzRawLogger(this)
}

object IzLogger extends IzLoggerConvenienceApi[IzLogger] {
  override protected def make(r: LogRouter, context: CustomContext): IzLogger = new IzLogger(r, context)
}