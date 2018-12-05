package com.github.pshirshov.izumi.idealingua.il.renderer

import com.github.pshirshov.izumi.functional.Renderable
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.{Anno, Streams, TypedStream}
import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._

class RStreams()(
  implicit ev: Renderable[TypedStream]
  , protected val evAnno: Renderable[Anno]
) extends Renderable[Streams] with WithMeta {
  override def render(streams: Streams): String = {
    val out =
      s"""streams ${streams.id.name} {
         |${streams.streams.map(_.render()).mkString("\n").shift(2)}
         |}
     """.stripMargin
    withMeta(streams.meta, out)
  }
}
