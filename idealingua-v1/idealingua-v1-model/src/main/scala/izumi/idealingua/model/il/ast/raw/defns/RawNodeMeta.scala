package izumi.idealingua.model.il.ast.raw.defns

import izumi.idealingua.model.il.ast.InputPosition

case class RawNodeMeta(doc: Option[String], annos: Seq[RawAnno], position: InputPosition)
