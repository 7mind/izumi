package izumi.distage.reflection.macros

import izumi.distage.model.providers.Functoid
import izumi.distage.reflection.macros.universe.{DIAnnotationMeta, FunctoidMacroBase}

import scala.reflect.macros.blackbox

final class FunctoidMacro(ctx: blackbox.Context) extends FunctoidMacroBase[Functoid](ctx) {
  override def tpe[A: c.WeakTypeTag]: c.Type = c.weakTypeOf[Functoid[A]]
  override def idAnnotationFqn: String = DIAnnotationMeta.idAnnotationFqn(ctx.universe)
}
