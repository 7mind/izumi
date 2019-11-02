package izumi.idealingua.model.il.ast.typed

import izumi.idealingua.model.il.ast.InputPosition

case class NodeMeta(doc: Option[String], annos: Seq[Anno], pos: InputPosition)

object NodeMeta {
  final val empty: NodeMeta = NodeMeta(None, Seq.empty, InputPosition.Undefined)
}
