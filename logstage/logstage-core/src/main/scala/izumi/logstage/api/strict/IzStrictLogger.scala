package izumi.logstage.api.strict

import izumi.logstage.api.Log.CustomContext
import izumi.logstage.api.logger.{AbstractMacroStrictLogger, LogRouter, RoutingLogger}
import izumi.logstage.api.rendering.StrictEncoded
import izumi.logstage.api.{IzLoggerConvenienceApi, IzRawLogger, Log}

class IzStrictLogger(
  override val router: LogRouter,
  override val customContext: Log.CustomContext,
) extends RoutingLogger
  with AbstractMacroStrictLogger {

  override type Self = IzStrictLogger

  override def withCustomContext(context: CustomContext): Self = new IzStrictLogger(router, customContext + context)
  final def withCustomContext(context: (String, StrictEncoded)*): Self = withCustomContextMap(context.toMap)
  final def withCustomContextMap(context: Map[String, StrictEncoded]): Self = withCustomContext(CustomContext.fromMap(context))

  final def apply(context: CustomContext): Self = withCustomContext(context)
  final def apply(context: (String, StrictEncoded)*): Self = withCustomContextMap(context.toMap)
  final def apply(context: Map[String, StrictEncoded]): Self = withCustomContextMap(context)

  final def raw: IzRawLogger = new IzRawLogger(this)
}

object IzStrictLogger extends IzLoggerConvenienceApi[IzStrictLogger] {
  override protected def make(r: LogRouter, context: CustomContext): IzStrictLogger = new IzStrictLogger(r, context)
}
