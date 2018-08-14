package com.github.pshirshov.izumi.idealingua.il.renderer

import com.github.pshirshov.izumi.functional.Renderable
import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
import com.github.pshirshov.izumi.idealingua.model.common._
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed._


class RService()(
  implicit protected val evSimpleStructure: Renderable[SimpleStructure]
  , protected val evAnno: Renderable[Anno]
  , protected val evTypeId: Renderable[TypeId]
  , protected val evAdtMember: Renderable[AdtMember]
) extends Renderable[Service] with WithMethodRenderer {
  override def kw: String = "def"

  override def render(service: Service): String = {
    val out =
      s"""service ${service.id.name} {
         |${service.methods.map(renderMethod).mkString("\n").shift(2)}
         |}
     """.stripMargin
    withComment(service.doc, out)
  }
}
