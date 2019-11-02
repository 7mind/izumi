package izumi.idealingua.il.renderer

import izumi.functional.Renderable
import izumi.idealingua.model.il.ast.typed.Field

class RField(context: IDLRenderingContext) extends Renderable[Field] {
  import context._

  override def render(field: Field): String = {
    val repr = s"${field.name}: ${field.typeId.render()}"
    context.meta.withMeta(field.meta, repr)
  }
}
