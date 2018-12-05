package com.github.pshirshov.izumi.idealingua.il.renderer

import com.github.pshirshov.izumi.functional.Renderable
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.{Anno, NodeMeta}

trait WithMeta {
  protected implicit def evAnno: Renderable[Anno]

  def withMeta(meta: NodeMeta, struct: String): String = {
    val maybeDoc = meta.doc.map {
      d =>
        s"""/*${d.split('\n').map(v => s"  *$v").mkString("\n").trim}
           |  */""".stripMargin
    }

    val maybeAnno = meta.annos.map(_.render())

    Seq(
      maybeDoc.toSeq
      , maybeAnno
      , Seq(struct)
    )
      .flatten
      .mkString("\n")
  }

}
