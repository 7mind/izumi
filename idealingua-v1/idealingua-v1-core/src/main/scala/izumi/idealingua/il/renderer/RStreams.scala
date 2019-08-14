package izumi.idealingua.il.renderer

import izumi.functional.Renderable
import izumi.fundamentals.platform.strings.IzString._
import izumi.idealingua.model.il.ast.typed.Streams

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
