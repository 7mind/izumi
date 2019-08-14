package com.github.pshirshov.izumi.idealingua.il.renderer

import com.github.pshirshov.izumi.functional.Renderable
import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.Streams

class RStreams(context: IDLRenderingContext) extends Renderable[Streams] {
  import context._

  override def render(streams: Streams): String = {
    val out =
      s"""streams ${streams.id.name} {
         |${streams.streams.map(_.render()).mkString("\n").shift(2)}
         |}
     """.stripMargin
    context.meta.withMeta(streams.meta, out)
  }
}
