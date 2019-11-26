package izumi.idealingua.il.renderer

import izumi.functional.Renderable
import izumi.fundamentals.platform.strings.IzString._
import izumi.idealingua.model.il.ast.typed._

class RService(context: IDLRenderingContext) extends Renderable[Service] {
  override def render(service: Service): String = {
    val out =
      s"""service ${service.id.name} {
         |${service.methods.map(context.methods.renderMethod("def", _)).mkString("\n").shift(2)}
         |}
     """.stripMargin
    context.meta.withMeta(service.meta, out)
  }
}
