package izumi.distage.model.reflection.universe

import izumi.reflect.macrortti.{LightTypeTag, LightTypeTagImpl}

private[distage] trait WithDISafeType {
  this: DIUniverseBase =>

  // TODO: hotspot, hashcode on keys is inefficient
  case class SafeType private (
    private[reflection] val typeNative: TypeNative,
    private[WithDISafeType] val tag: LightTypeTag,
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

//    final def =:=(that: SafeType): Boolean = {
//      tag =:= that.tag
//    }
//
//    final def <:<(that: SafeType): Boolean = {
//      tag <:< that.tag
//    }
  }

  object SafeType {
    def create(tpe: TypeNative): SafeType = {
      new SafeType(tpe, LightTypeTagImpl.makeLightTypeTag(u)(tpe))
    }
  }

}
