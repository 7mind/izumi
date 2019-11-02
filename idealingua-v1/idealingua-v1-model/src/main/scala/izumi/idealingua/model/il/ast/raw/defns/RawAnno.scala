package izumi.idealingua.model.il.ast.raw.defns

import izumi.idealingua.model.il.ast.InputPosition

case class RawAnno(name: String, values: RawVal.CMap, position: InputPosition)
