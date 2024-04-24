package izumi.distage.model.reflection

import izumi.fundamentals.platform.functional.Identity
import izumi.reflect.macrortti.LightTypeTag
import izumi.reflect.{AnyTag, Tag, TagK, WeakTag}

final class SafeType(private[distage] val anyTag: AnyTag) {
  @inline def tag: LightTypeTag = anyTag.tag
  @inline def closestClass: Class[?] = anyTag.closestClass
  @inline def hasPreciseClass: Boolean = anyTag.hasPreciseClass
  @inline def =:=(that: SafeType): Boolean = anyTag =:= that.anyTag
  @inline def <:<(that: SafeType): Boolean = anyTag <:< that.anyTag

  override def equals(obj: Any): Boolean = {
    obj match {
      case that: SafeType =>
        anyTag == that.anyTag
      case _ =>
        false
    }
  }

  // hashcode is already cached in the underlying code
  @inline override def hashCode: Int = {
    anyTag.hashCode()
  }

  /**
    *  The only difference between SafeType and AnyTag is `toString`:
    * - SafeType's toString does not have the wrapping 'Tag/HKTag' label
    * - SafeType's toString uses `.tag.repr` instead of `.tag.toString`
    */
  @inline override def toString: String = {
    anyTag.tag.repr
  }
}

trait SafeTypeTools {
  def get[T: Tag]: SafeType = new SafeType(Tag[T])
  def getK[K[_]: TagK]: SafeType = new SafeType(TagK[K])
  def unsafeGetWeak[T](implicit weakTag: WeakTag[T]): SafeType = new SafeType(WeakTag[T])
}

object SafeType extends SafeTypeTools {
  lazy val identityEffectType: SafeType = SafeType.getK[Identity]
}
