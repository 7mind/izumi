package com.github.pshirshov.izumi.idealingua.il.renderer

import com.github.pshirshov.izumi.functional.Renderable
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.Field

class RField(context: IDLRenderingContext) extends Renderable[Field] {
  import context._

  override def render(field: Field): String = {
    val repr = s"${field.name}: ${field.typeId.render()}"
    context.meta.withMeta(field.meta, repr)
  }
}
