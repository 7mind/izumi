package com.github.pshirshov.izumi.idealingua.il.renderer

import com.github.pshirshov.izumi.functional.Renderable
import com.github.pshirshov.izumi.idealingua.model.common.StreamDirection
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.{Anno, SimpleStructure, TypedStream}

class RTypedStream()(
  implicit ev: Renderable[StreamDirection]
  , ev1: Renderable[SimpleStructure]
  , protected val evAnno: Renderable[Anno]
) extends Renderable[TypedStream] with WithMeta {
  override def render(ts: TypedStream): String = {
    ts match {
      case m: TypedStream.Directed =>
        val out = s"${m.direction.render()} ${m.name}(${m.signature.render()})"
        withMeta(m.meta, out)
    }
  }
}
