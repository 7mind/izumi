package izumi.distage.model.reflection.universe

import izumi.fundamentals.reflection.SafeType0
import izumi.fundamentals.reflection.macrortti.{LightTypeTag, LightTypeTagImpl}

trait WithDISafeType {
  this: DIUniverseBase
    with WithTags =>

  // TODO: hotspot, hashcode on keys is inefficient
  case class SafeType protected(override val tpe: TypeNative, override val tag: LightTypeTag) extends SafeType0[u.type](tpe, tag)

  object SafeType {
    // FIXME TODO constructing SafeType from a runtime type tag
    @deprecated("constructing SafeType from a runtime type tag", "0.9.0")
    def apply(tpe: TypeNative): SafeType = {
      new SafeType(tpe, LightTypeTagImpl.makeLightTypeTag(u)(tpe))
    }

    def get[T: Tag]: SafeType = SafeType(Tag[T].tpe.tpe, Tag[T].tag)

    def getK[K[_] : TagK]: SafeType = SafeType(TagK[K].tpe.tpe, TagK[K].tag)

    def unsafeGetWeak[T](implicit weakTag: WeakTag[T]): SafeType = SafeType(weakTag.tpe.tpe, weakTag.tag)
  }

}
