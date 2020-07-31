package izumi.logstage.api.logger

import izumi.logstage.api.Log.CustomContext
import izumi.logstage.api.rendering.AnyEncoded

trait EncodingAwareRoutingLogger[E <: AnyEncoded] extends RoutingLogger {

  override type Self <: EncodingAwareRoutingLogger[E]

  final def withCustomContext(context: (String, E)*): Self = withCustomContextMap(context.toMap)
  final def withCustomContextMap(context: Map[String, E]): Self = withCustomContext(CustomContext.fromMap(context))

  final def apply(context: (String, E)*): Self = withCustomContextMap(context.toMap)
  final def apply(context: Map[String, E]): Self = withCustomContextMap(context)

  /** Summon a wrapper that logs raw messages without introspection of string expressions */
  final def raw: RawLogger[E] = new RawLogger(this)
}
