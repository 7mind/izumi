package izumi.logstage.api.logger

import izumi.logstage.api.Log.CustomContext
import izumi.logstage.api.rendering.AnyEncoded

trait EncodingAwareAbstractLogger[E <: AnyEncoded] extends AbstractLogger {

  override type Self <: EncodingAwareAbstractLogger[E]

  final def withCustomContext(context: (String, E)*): Self = withCustomContextMap(context.toMap)
  final def withCustomContextMap(context: Map[String, E]): Self = withCustomContext(CustomContext.fromMap(context))

  final def apply(context: (String, E)*): Self = withCustomContextMap(context.toMap)
  final def apply(context: Map[String, E]): Self = withCustomContextMap(context)
}
