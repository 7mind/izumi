package izumi.distage.reflection.macros

import izumi.distage.model.providers.Functoid
import izumi.distage.reflection.macros.universe.{DIAnnotationMeta, FunctoidMacroBase}

import scala.reflect.macros.blackbox

class FunctoidMacro(ctx: blackbox.Context) extends FunctoidMacroBase(ctx) {
  override def tpe[A: c.WeakTypeTag]: c.Type = c.weakTypeOf[Functoid[A]]

  override def idAnnotationFqn: String = new DIAnnotationMeta(ctx.universe).idAnnotationFqn
}
