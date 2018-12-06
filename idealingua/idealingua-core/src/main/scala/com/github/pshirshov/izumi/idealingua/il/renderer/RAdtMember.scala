package com.github.pshirshov.izumi.idealingua.il.renderer

import com.github.pshirshov.izumi.functional.Renderable
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.AdtMember


class RAdtMember(context: IDLRenderingContext) extends Renderable[AdtMember] {
  import context._
  override def render(field: AdtMember): String = {
    val t = field.typeId.render()
    val repr = field.memberName match {
      case Some(name) =>
        s"$t as $name"
      case None =>
        t
    }

    context.meta.withMeta(field.meta, repr)
  }
}
