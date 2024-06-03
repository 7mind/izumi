package izumi.distage.reflection.macros.universe.basicuniverse

import izumi.reflect.macrortti.{LightTypeTag, LightTypeTagImpl}

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
  def create(u: scala.reflect.api.Universe)(tpe: scala.reflect.api.Universe#Type): MacroSafeType = {
    import u.*
    val modelReflectionPkg = q"_root_.izumi.distage.model.reflection"

    val ltt = LightTypeTagImpl.makeLightTypeTag(u)(tpe.asInstanceOf[u.Type])
    val tagTree = q"{ $modelReflectionPkg.SafeType.get[${u.Liftable.liftType(tpe.asInstanceOf[u.Type])}] }"

    new MacroSafeType(tagTree, ltt)
  }
}
