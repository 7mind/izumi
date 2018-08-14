package com.github.pshirshov.izumi.idealingua.il.renderer

import com.github.pshirshov.izumi.functional.Renderable
import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
import com.github.pshirshov.izumi.idealingua.model.common._
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed._


class REmitter()(
  implicit protected val evSimpleStructure: Renderable[SimpleStructure]
  , protected val evAnno: Renderable[Anno]
  , protected val evTypeId: Renderable[TypeId]
  , protected val evAdtMember: Renderable[AdtMember]
) extends Renderable[Emitter] with WithMethodRenderer {

  override def kw: String = "event"

  override def render(emitter: Emitter): String = {
    val out =
      s"""emitter ${emitter.id.name} {
         |${emitter.events.map(renderMethod).mkString("\n").shift(2)}
         |}
     """.stripMargin
    withComment(emitter.doc, out)
  }
}
