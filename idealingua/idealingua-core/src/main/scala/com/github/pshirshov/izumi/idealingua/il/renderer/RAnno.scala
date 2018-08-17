package com.github.pshirshov.izumi.idealingua.il.renderer

import com.github.pshirshov.izumi.functional.Renderable
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.Anno

class RAnno() extends Renderable[Anno] {
  override def render(anno: Anno): String = {
    val vals = anno.values.map {
      case (name, v) =>
        s"$name = ${RValue.renderValue(v)}"
    }.mkString(",")

    s"@${anno.name}($vals)"
  }
}
