package izumi.distage.model.reflection.universe

import izumi.fundamentals.platform.cache.{CachedHashcode, CachedRepr}
import izumi.reflect.macrortti.{LightTypeTag, LightTypeTagImpl}

private[distage] trait WithDISafeType { this: DIUniverseBase =>

  // TODO: hotspot, hashcode on keys is inefficient
  case class SafeType private (
    private[reflection] val typeNative: TypeNative,
    private[WithDISafeType] val tag: LightTypeTag,
  ) extends CachedHashcode
    with CachedRepr {

    override final protected def hash: Int = tag.hashCode()
    override final protected def repr: String = tag.repr

    override final def equals(obj: Any): Boolean = {
      obj match {
        case that: SafeType =>
          tag =:= that.tag
        case _ =>
          false
      }
    }
  }

  object SafeType {
    def create(tpe: TypeNative): SafeType = {
      new SafeType(tpe, LightTypeTagImpl.makeLightTypeTag(u)(tpe))
    }
  }

}
