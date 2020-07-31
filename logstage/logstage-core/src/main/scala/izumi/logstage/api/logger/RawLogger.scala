package izumi.logstage.api.logger

import izumi.logstage.api.Log.CustomContext
import izumi.logstage.api.rendering.AnyEncoded

final class RawLogger[E <: AnyEncoded](delegate: EncodingAwareRoutingLogger[E]) extends EncodingAwareRoutingLogger[E] with AbstractMacroRawLogger {
  override type Self = RawLogger[E]

  def router: LogRouter = delegate.router
  def customContext: CustomContext = delegate.customContext

  def withCustomContext(context: CustomContext): RawLogger[E] = new RawLogger(delegate.withCustomContext(context))
}
