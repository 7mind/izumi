package izumi.distage.model.definition

import izumi.reflect.TagMacro

import scala.reflect.macros.blackbox

object LifecycleTagMacro {
  def fakeResourceTagMacroIntellijWorkaroundImpl[R <: Lifecycle[Any, Any]: c.WeakTypeTag](c: blackbox.Context): c.Expr[Nothing] = {
    val tagMacro = new TagMacro(c)
    tagMacro.makeTagImpl[R] // run the macro AGAIN, to get a fresh error message
    val tagTrace = tagMacro.getImplicitError()

    c.abort(c.enclosingPosition, s"could not find implicit ResourceTag for ${c.universe.weakTypeOf[R]}!\n$tagTrace")
  }
}
