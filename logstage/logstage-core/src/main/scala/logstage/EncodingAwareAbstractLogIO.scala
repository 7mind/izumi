package logstage

import izumi.logstage.api.Log.CustomContext
import izumi.logstage.api.rendering.AnyEncoded

trait EncodingAwareAbstractLogIO[F[_], E <: AnyEncoded] extends AbstractLogIO[F] {
  override type Self[f[_]] <: EncodingAwareAbstractLogIO[f, E]

  final def withCustomContext(context: (String, E)*): Self[F] = withCustomContextMap(context.toMap)
  final def withCustomContextMap(context: Map[String, E]): Self[F] = withCustomContext(CustomContext.fromMap(context))
  final def apply(context: (String, E)*): Self[F] = withCustomContextMap(context.toMap)
  final def apply(context: Map[String, E]): Self[F] = withCustomContextMap(context)
}
