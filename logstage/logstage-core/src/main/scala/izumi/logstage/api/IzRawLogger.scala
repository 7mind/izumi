package izumi.logstage.api

import izumi.logstage.api.Log.CustomContext
import izumi.logstage.api.logger.{AbstractMacroRawLogger, LogRouter, RoutingLogger}
import izumi.logstage.api.rendering.AnyEncoded

final private[api] class IzRawLogger(routingLogger: RoutingLogger) extends RoutingLogger with AbstractMacroRawLogger {
  override type Self = IzRawLogger

  override def router: LogRouter = routingLogger.router
  override def customContext: CustomContext = routingLogger.customContext

  override def withCustomContext(context: CustomContext): Self = new IzRawLogger(routingLogger.withCustomContext(context))
  def withCustomContext(context: (String, AnyEncoded)*): Self = withCustomContextMap(context.toMap)
  def withCustomContextMap(context: Map[String, AnyEncoded]): Self = withCustomContext(CustomContext.fromMap(context))

  def apply(context: CustomContext): Self = withCustomContext(context)
  def apply(context: (String, AnyEncoded)*): Self = withCustomContextMap(context.toMap)
}

