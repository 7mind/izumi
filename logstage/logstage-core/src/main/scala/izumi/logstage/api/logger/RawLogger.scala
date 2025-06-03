package izumi.logstage.api.logger

import izumi.fundamentals.platform.language.CodePosition
import izumi.logstage.api.Log
import izumi.logstage.api.Log.CustomContext
import izumi.logstage.api.rendering.AnyEncoded
import izumi.logstage.macros.EncodingMode

final class RawLogger[E <: AnyEncoded](delegate: EncodingAwareAbstractLogger[E]) extends EncodingAwareAbstractLogger[E] with AbstractMacroLogger {
  override type Self = RawLogger[E]

  override type EncMode = EncodingMode.Raw.type

  def unsafeLog(entry: Log.Entry): Unit = delegate.unsafeLog(entry)
  def acceptable(position: CodePosition, logLevel: Log.Level): Boolean = delegate.acceptable(position, logLevel)

  def withCustomContext(context: CustomContext): Self = new RawLogger(delegate.withCustomContext(context))

}
