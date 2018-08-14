package com.github.pshirshov.izumi.idealingua.il.renderer

import com.github.pshirshov.izumi.functional.Renderable
import com.github.pshirshov.izumi.idealingua.model.common.StructureId
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.{Field, SimpleStructure}

class RSimpleStructure()(
  implicit ev1: Renderable[Field]
  , ev2: Renderable[StructureId]
) extends Renderable[SimpleStructure] {
  override def render(signature: SimpleStructure): String = {
    Seq(
      signature.concepts.map(_.render()).map(t => s"+ $t")
      , signature.fields.map(_.render())
    ).flatten.mkString(", ")
  }
}
