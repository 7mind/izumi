package com.github.pshirshov.izumi.idealingua.il.renderer

import com.github.pshirshov.izumi.functional.Renderable
import com.github.pshirshov.izumi.idealingua.model.common.TypeId
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.{AdtMember, Anno}


class RAdtMember()(implicit ev: Renderable[TypeId], protected val evAnno: Renderable[Anno]) extends Renderable[AdtMember] with WithMeta {
  override def render(field: AdtMember): String = {
    val t = field.typeId.render()
    val repr = field.memberName match {
      case Some(name) =>
        s"$t as $name"
      case None =>
        t
    }

    withMeta(field.meta, repr)
  }
}
