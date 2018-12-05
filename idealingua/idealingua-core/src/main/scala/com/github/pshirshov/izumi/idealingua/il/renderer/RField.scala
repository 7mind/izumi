package com.github.pshirshov.izumi.idealingua.il.renderer

import com.github.pshirshov.izumi.functional.Renderable
import com.github.pshirshov.izumi.idealingua.model.common.TypeId
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.{Anno, Field}

class RField()(
  implicit ev: Renderable[TypeId],
  protected val evAnno: Renderable[Anno],
) extends Renderable[Field] with WithMeta {
  override def render(field: Field): String = {
    val repr = s"${field.name}: ${field.typeId.render()}"
    withMeta(field.meta, repr)
  }
}
