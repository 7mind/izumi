package com.github.pshirshov.izumi.idealingua.il.renderer

import com.github.pshirshov.izumi.functional.Renderable
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypedStream

class RTypedStream(context: IDLRenderingContext) extends Renderable[TypedStream] {
  import context._

  override def render(ts: TypedStream): String = {
    ts match {
      case m: TypedStream.Directed =>
        val out = s"${m.direction.render()} ${m.name}(${m.signature.render()})"
        context.meta.withMeta(m.meta, out)
    }
  }
}
