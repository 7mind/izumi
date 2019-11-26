package izumi.idealingua.il.renderer

import izumi.functional.Renderable
import izumi.idealingua.model.il.ast.typed.{Anno, ConstValue}

class RAnno(context: IDLRenderingContext) extends Renderable[Anno] {
  import context._
  override def render(anno: Anno): String = {
    val vals = anno.values.map {
      case (name, v: ConstValue.Typed) =>
        s"$name: ${v.typeId.render()} = ${(v: ConstValue).render()}"
      case (name, v) =>
        s"$name = ${v.render()}"
    }.mkString(", ")

    s"@${anno.name}($vals)"
  }
}
