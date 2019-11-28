package izumi.distage.model.reflection.universe

import izumi.fundamentals.reflection.SafeType0
import izumi.fundamentals.reflection.Tags.{Tag, TagK, WeakTag}
import izumi.fundamentals.reflection.macrortti.{LightTypeTag, LightTypeTagImpl}

trait WithDISafeType {
  this: DIUniverseBase =>

  // TODO: hotspot, hashcode on keys is inefficient
  case class SafeType private (tpe: TypeNative, override val tag: LightTypeTag) extends SafeType0[u.type](tpe, tag)

  object SafeType {
    // FIXME TODO constructing SafeType from a runtime type tag
    @deprecated("constructing SafeType from a runtime type tag", "0.9.0")
    def apply(tpe: TypeNative): SafeType = {
      new SafeType(tpe, LightTypeTagImpl.makeLightTypeTag(u)(tpe))
    }

    @deprecated("constructing SafeType from a LightTypeTag", "0.10.0")
    def apply(tag: LightTypeTag): SafeType = new SafeType(null, tag)

    def get[T: Tag]: SafeType = SafeType(null, Tag[T].tag)
    def getK[K[_]: TagK]: SafeType = SafeType(null, TagK[K].tag)
    def unsafeGetWeak[T](implicit weakTag: WeakTag[T]): SafeType = SafeType(null, weakTag.tag)
  }

}
