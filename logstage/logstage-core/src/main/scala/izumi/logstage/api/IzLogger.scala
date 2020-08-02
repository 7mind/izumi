package izumi.logstage.api

import izumi.logstage.api.Log.CustomContext
import izumi.logstage.api.logger.{AbstractMacroLogger, EncodingAwareAbstractLogger, LogRouter, RawLogger, RoutingLogger}
import izumi.logstage.api.rendering.AnyEncoded

class IzLogger(
  override val router: LogRouter,
  override val customContext: Log.CustomContext,
) extends RoutingLogger
  with EncodingAwareAbstractLogger[AnyEncoded]
  with AbstractMacroLogger {

  override type Self = IzLogger

  def withCustomContext(context: CustomContext): Self = new IzLogger(router, customContext + context)

  def raw: RawLogger[AnyEncoded] = new RawLogger(this)
}

object IzLogger extends IzLoggerConvenienceApi[IzLogger] {
  override protected def make(r: LogRouter, context: CustomContext): IzLogger = new IzLogger(r, context)
}
