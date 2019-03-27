package com.github.pshirshov.izumi.idealingua.il.renderer

import com.github.pshirshov.izumi.functional.Renderable
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.IdField

class RIdField(context: IDLRenderingContext) extends Renderable[IdField] {
  import context._

  override def render(field: IdField): String = {
    val repr = s"${field.name}: ${field.typeId.render()}"
    context.meta.withMeta(field.meta, repr)
  }
}
