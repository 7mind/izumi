package izumi.distage.reflection.macros.universe.impl

import izumi.reflect.macrortti.{LightTypeTag, LightTypeTagImpl}

import scala.reflect.macros.blackbox

final class MacroSafeType private (
  private[reflection] val tagTree: scala.reflect.api.Universe#Tree,
  private val tag: LightTypeTag,
) {

  // hashcode is already cached in the underlying code
  @inline override def hashCode: Int = {
    tag.hashCode()
  }

  @inline override def toString: String = {
    tag.repr
  }
  override def equals(obj: Any): Boolean = {
    obj match {
      case that: MacroSafeType =>
        tag =:= that.tag
      case _ =>
        false
    }
  }
}

object MacroSafeType {
  def create(ctx: blackbox.Context)(tpe: ctx.Type): MacroSafeType = {
    import ctx.universe.*
    val modelReflectionPkg: ctx.Tree = q"_root_.izumi.distage.model.reflection"

    val ltt = LightTypeTagImpl.makeLightTypeTag(ctx.universe)(tpe)
    val tagTree = q"{ $modelReflectionPkg.SafeType.get[${ctx.universe.Liftable.liftType(tpe)}] }"

    new MacroSafeType(tagTree, ltt)
  }
}
