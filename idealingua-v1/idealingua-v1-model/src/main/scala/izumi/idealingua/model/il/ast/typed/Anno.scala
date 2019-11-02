package izumi.idealingua.model.il.ast.typed

import izumi.idealingua.model.il.ast.InputPosition

case class Anno(name: String, values: Map[String, ConstValue], position: InputPosition)
