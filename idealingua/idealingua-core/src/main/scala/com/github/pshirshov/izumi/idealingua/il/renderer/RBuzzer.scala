package com.github.pshirshov.izumi.idealingua.il.renderer

import com.github.pshirshov.izumi.functional.Renderable
import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed._


class RBuzzer(context: IDLRenderingContext) extends Renderable[Buzzer] {
  override def render(buzzer: Buzzer): String = {
    val out =
      s"""buzzer ${buzzer.id.name} {
         |${buzzer.events.map(context.methods.renderMethod("line", _)).mkString("\n").shift(2)}
         |}
     """.stripMargin
    context.meta.withMeta(buzzer.meta, out)
  }
}
