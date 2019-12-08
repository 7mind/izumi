package izumi.distage.model.reflection.universe

import izumi.fundamentals.reflection.Tags.{Tag, TagK, WeakTag}
import izumi.fundamentals.reflection.macrortti.{LightTypeTag, LightTypeTagImpl}

trait WithDISafeType {
  this: DIUniverseBase =>

  // TODO: hotspot, hashcode on keys is inefficient
  case class SafeType private (
                                private val tpe: TypeNative,
                                tag: LightTypeTag,
                                @deprecated("direct access to SafeType.cls", "now")
                                private[distage] val cls: Class[_],
                              ) {

    override final val hashCode: Int = tag.hashCode()
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

    final def hasPreciseClass: Boolean = {
      tag.shortName == cls.getSimpleName
    }

    @deprecated("Avoid using runtime reflection, this will be removed in future", "0.9.0")
    def use[T](f: TypeNative => T): T = f(tpe)
  }

  object SafeType {
    // FIXME TODO constructing SafeType from a runtime type tag
    @deprecated("constructing SafeType from a runtime type tag", "0.9.0")
    def apply(tpe: TypeNative): SafeType = {
      new SafeType(tpe, LightTypeTagImpl.makeLightTypeTag(u)(tpe), shitClass)
    }
    private[this] val shitClass = classOf[Any]

    @deprecated("constructing SafeType from a LightTypeTag", "0.10.0")
    def apply(tag: LightTypeTag): SafeType = new SafeType(null, tag, shitClass)

    def get[T: Tag]: SafeType = SafeType(null, Tag[T].tag, Tag[T].closestClass)
    def getK[K[_]: TagK]: SafeType = SafeType(null, TagK[K].tag, TagK[K].closestClass)
    def unsafeGetWeak[T](implicit weakTag: WeakTag[T]): SafeType = SafeType(null, weakTag.tag, weakTag.closestClass)
  }

}
