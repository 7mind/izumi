package com.github.pshirshov.izumi.idealingua.il.renderer

import com.github.pshirshov.izumi.functional.Renderable
import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed._


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
