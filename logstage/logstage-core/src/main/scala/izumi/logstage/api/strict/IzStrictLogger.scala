package izumi.logstage.api.strict

import izumi.logstage.api.Log.CustomContext
import izumi.logstage.api.logger.{AbstractMacroStrictLogger, EncodingAwareRoutingLogger, LogRouter}
import izumi.logstage.api.rendering.StrictEncoded
import izumi.logstage.api.{IzLoggerConvenienceApi, Log}

class IzStrictLogger(
  override val router: LogRouter,
  override val customContext: Log.CustomContext,
) extends EncodingAwareRoutingLogger[StrictEncoded]
  with AbstractMacroStrictLogger {

  override type Self = IzStrictLogger

  def withCustomContext(context: CustomContext): Self = new IzStrictLogger(router, customContext + context)
}

object IzStrictLogger extends IzLoggerConvenienceApi[IzStrictLogger] {
  override protected def make(r: LogRouter, context: CustomContext): IzStrictLogger = new IzStrictLogger(r, context)
}
