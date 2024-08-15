package izumi.logstage.api.logger

import izumi.fundamentals.platform.language.CodePosition
import izumi.logstage.api.Log
import izumi.logstage.api.Log.CustomContext
import izumi.logstage.api.rendering.AnyEncoded

final class RawLogger[E <: AnyEncoded](delegate: EncodingAwareAbstractLogger[E]) extends EncodingAwareAbstractLogger[E] with AbstractMacroRawLogger {
  override type Self = RawLogger[E]

  def unsafeLog(entry: Log.Entry): Unit = delegate.unsafeLog(entry)
  def acceptable(loggerId: Log.LoggerId, logLevel: Log.Level): Boolean = delegate.acceptable(loggerId, logLevel)
  def acceptable(loggerId: Log.LoggerId, line: Int, logLevel: Log.Level): Boolean = delegate.acceptable(loggerId, line, logLevel)
  def acceptable(position: CodePosition, logLevel: Log.Level): Boolean = delegate.acceptable(position, logLevel)

  def withCustomContext(context: CustomContext): Self = new RawLogger(delegate.withCustomContext(context))

}
