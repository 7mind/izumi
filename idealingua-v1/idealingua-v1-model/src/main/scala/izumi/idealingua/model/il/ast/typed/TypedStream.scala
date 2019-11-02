package izumi.idealingua.model.il.ast.typed

import izumi.idealingua.model.common.StreamDirection

sealed trait TypedStream

object TypedStream {
  final case class Directed(name: String, direction: StreamDirection, signature: SimpleStructure, meta: NodeMeta) extends TypedStream
}
