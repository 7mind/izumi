package izumi.logstage.api.strict

import izumi.logstage.api.Log.CustomContext
import izumi.logstage.api.logger.{AbstractMacroLogger, EncodingAwareAbstractLogger, LogRouter, RawLogger, RoutingLogger}
import izumi.logstage.api.rendering.StrictEncoded
import izumi.logstage.api.{IzLoggerConvenienceApi, Log}
import izumi.logstage.macros.EncodingMode

/**
  * A variant of [[logstage.IzLogger]] that renders values only
  * using [[izumi.logstage.api.rendering.LogstageCodec]] typeclass instances
  * for those values. If an instance is missing, compilation will fail, whereas
  * with default `IzLogger`, the rendering will fall back to using `toString` method
  */
class IzStrictLogger(
  override val router: LogRouter,
  override val customContext: Log.CustomContext,
) extends RoutingLogger
  with EncodingAwareAbstractLogger[StrictEncoded]
  with AbstractMacroLogger {

  override type Self = IzStrictLogger

  override type EncMode = EncodingMode.Strict.type

  def withCustomContext(context: CustomContext): Self = new IzStrictLogger(router, customContext + context)

  def raw: RawLogger[StrictEncoded] = new RawLogger(this)
}

object IzStrictLogger extends IzLoggerConvenienceApi[IzStrictLogger] {
  override protected def make(r: LogRouter, context: CustomContext): IzStrictLogger = new IzStrictLogger(r, context)
}
