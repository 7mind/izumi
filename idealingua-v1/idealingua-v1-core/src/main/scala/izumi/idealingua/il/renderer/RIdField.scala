package izumi.idealingua.il.renderer

import izumi.functional.Renderable
import izumi.idealingua.model.il.ast.typed.IdField

class RIdField(context: IDLRenderingContext) extends Renderable[IdField] {
  import context._

  override def render(field: IdField): String = {
    val repr = s"${field.name}: ${field.typeId.render()}"
    context.meta.withMeta(field.meta, repr)
  }
}
