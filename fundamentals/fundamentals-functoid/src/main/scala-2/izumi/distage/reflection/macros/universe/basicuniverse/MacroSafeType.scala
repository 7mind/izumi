package izumi.distage.reflection.macros.universe.basicuniverse

import izumi.reflect.macrortti.{LightTypeTag, LightTypeTagImpl}

final class MacroSafeType private (
  private[reflection] val tagTree0: scala.reflect.api.Universe#Tree,
  private val tag: LightTypeTag,
) {

  // hashcode is already cached in the underlying code
  @inline override def hashCode: Int = {
    tag.hashCode()
  }

  @inline override def toString: String = {
    tag.repr
  }

  @inline def tagTree(u: scala.reflect.api.Universe): u.Tree = tagTree0.asInstanceOf[u.Tree]

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
  def create(u: scala.reflect.api.Universe)(tpe: u.Type): MacroSafeType = {
    import u.*
    val modelReflectionPkg = q"_root_.izumi.distage.model.reflection"

    val ltt = LightTypeTagImpl.makeLightTypeTag(u)(tpe)
    val tagTree = q"{ $modelReflectionPkg.SafeType.get[${u.Liftable.liftType(tpe)}] }"

    new MacroSafeType(tagTree, ltt)
  }
}
