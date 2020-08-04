package izumi.logstage.api.logger

import izumi.logstage.api.Log
import izumi.logstage.api.Log.CustomContext
import izumi.logstage.api.rendering.AnyEncoded

final class RawLogger[E <: AnyEncoded](delegate: EncodingAwareAbstractLogger[E]) extends EncodingAwareAbstractLogger[E] with AbstractMacroRawLogger {
  override type Self = RawLogger[E]

  def unsafeLog(entry: Log.Entry): Unit = delegate.unsafeLog(entry)
  def acceptable(loggerId: Log.LoggerId, logLevel: Log.Level): Boolean = delegate.acceptable(loggerId, logLevel)

  def withCustomContext(context: CustomContext): Self = new RawLogger(delegate.withCustomContext(context))
}
