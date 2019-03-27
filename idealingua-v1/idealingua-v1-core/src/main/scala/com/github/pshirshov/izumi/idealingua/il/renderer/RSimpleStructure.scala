package com.github.pshirshov.izumi.idealingua.il.renderer

import com.github.pshirshov.izumi.functional.Renderable
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.SimpleStructure

class RSimpleStructure(context: IDLRenderingContext) extends Renderable[SimpleStructure] {
  import context._

  override def render(signature: SimpleStructure): String = {
    Seq(
      signature.concepts.map(_.render()).map(t => s"+ $t")
      , signature.fields.map(_.render())
    ).flatten.mkString(", ")
  }
}
