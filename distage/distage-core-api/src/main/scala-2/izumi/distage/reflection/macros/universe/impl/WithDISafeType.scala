package izumi.distage.reflection.macros.universe.impl

import izumi.reflect.macrortti.{LightTypeTag, LightTypeTagImpl}

private[distage] trait WithDISafeType { this: DIUniverseBase =>

  class MacroSafeType private (
    private[reflection] val typeNative: TypeNative,
    private[MacroSafeType] val tag: LightTypeTag,
  ) {

    // hashcode is already cached in the underlying code
    @inline override def hashCode: Int = {
      tag.hashCode()
    }

    @inline override def toString: String = {
      tag.repr
    }
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
