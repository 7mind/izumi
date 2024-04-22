package izumi.distage.reflection.macros.universe.impl

import izumi.fundamentals.platform.cache.{CachedHashcode, CachedRepr}
import izumi.reflect.macrortti.{LightTypeTag, LightTypeTagImpl}

private[distage] trait WithDISafeType { this: DIUniverseBase =>

  // TODO: hotspot, hashcode on keys is inefficient
  class MacroSafeType private(
    private[reflection] val typeNative: TypeNative,
    private[MacroSafeType] val tag: LightTypeTag,
  ) extends CachedHashcode
    with CachedRepr {

    override final protected def hash: Int = tag.hashCode()
    override final protected def repr: String = tag.repr

    override final def equals(obj: Any): Boolean = {
      obj match {
        case that: MacroSafeType =>
          tag =:= that.tag
        case _ =>
          false
      }
    }
  }

  object MacroSafeType {
    def create(tpe: TypeNative): MacroSafeType = {
      new MacroSafeType(tpe, LightTypeTagImpl.makeLightTypeTag(u)(tpe))
    }
  }

}
