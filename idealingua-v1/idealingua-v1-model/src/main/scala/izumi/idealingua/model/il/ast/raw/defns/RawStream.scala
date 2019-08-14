package izumi.idealingua.model.il.ast.raw.defns

import izumi.idealingua.model.common.StreamDirection

sealed trait RawStream

object RawStream {
  final case class Directed(name: String, direction: StreamDirection, signature: RawSimpleStructure, meta: RawNodeMeta) extends RawStream
}
