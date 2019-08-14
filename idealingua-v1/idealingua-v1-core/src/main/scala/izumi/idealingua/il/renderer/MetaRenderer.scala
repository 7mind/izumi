package izumi.idealingua.il.renderer

import izumi.idealingua.model.il.ast.typed.NodeMeta

class MetaRenderer(context: IDLRenderingContext) {
  import context._

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
