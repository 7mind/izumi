package com.github.pshirshov.izumi.idealingua.il.renderer

import com.github.pshirshov.izumi.functional.Renderable
import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
import com.github.pshirshov.izumi.idealingua.model.common._
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed._


class RBuzzer()(
  implicit protected val evSimpleStructure: Renderable[SimpleStructure]
  , protected val evAnno: Renderable[Anno]
  , protected val evTypeId: Renderable[TypeId]
  , protected val evAdtMember: Renderable[AdtMember]
) extends Renderable[Buzzer] with WithMethodRenderer {

  override def kw: String = "line"

  override def render(buzzer: Buzzer): String = {
    val out =
      s"""buzzer ${buzzer.id.name} {
         |${buzzer.events.map(renderMethod).mkString("\n").shift(2)}
         |}
     """.stripMargin
    withMeta(buzzer.meta, out)
  }
}
