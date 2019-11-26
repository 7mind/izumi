package izumi.idealingua.il.renderer

import izumi.functional.Renderable
import izumi.idealingua.model.il.ast.typed.SimpleStructure

class RSimpleStructure(context: IDLRenderingContext) extends Renderable[SimpleStructure] {
  import context._

  override def render(signature: SimpleStructure): String = {
    Seq(
      signature.concepts.map(_.render()).map(t => s"+ $t")
      , signature.fields.map(_.render())
    ).flatten.mkString(", ")
  }
}
