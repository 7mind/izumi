package izumi.distage.model.reflection.universe

import izumi.fundamentals.reflection.macrortti.{LightTypeTag, LightTypeTagImpl}

private[distage] trait WithDISafeType {
  this: DIUniverseBase =>

  // TODO: hotspot, hashcode on keys is inefficient
  case class SafeType private (
                                private[distage] val typeNative: TypeNative,
                                tag: LightTypeTag,
                              ) {

    override final lazy val hashCode: Int = tag.hashCode()
    override final lazy val toString: String = tag.repr

    override final def equals(obj: Any): Boolean = {
      obj match {
        case that: SafeType =>
          tag =:= that.tag
        case _ =>
          false
      }
    }

    final def =:=(that: SafeType): Boolean = {
      tag =:= that.tag
    }

    final def <:<(that: SafeType): Boolean = {
      tag <:< that.tag
    }

    @deprecated("Avoid using runtime reflection, this will be removed in future", "0.9.0")
    def use[T](f: TypeNative => T): T = f(typeNative)
  }

  object SafeType {
    def apply(tpe: TypeNative): SafeType = {
      new SafeType(tpe, LightTypeTagImpl.makeLightTypeTag(u)(tpe))
    }
  }

}
