package izumi.distage.reflection.macros

import izumi.distage.model.providers.Functoid
import izumi.distage.reflection.macros.universe.DIAnnotationMeta

import scala.reflect.macros.blackbox

class FunctoidMacro(ctx: blackbox.Context) extends FunctoidMacroBase(ctx, new DIAnnotationMeta(ctx.universe).idAnnotationFqn) {
  override implicit def tpe[A: c.WeakTypeTag]: c.Type = c.weakTypeOf[Functoid[A]]
}
