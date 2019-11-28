package izumi.fundamentals.reflection

import izumi.fundamentals.reflection.macrortti.LightTypeTag.ReflectionLock
import izumi.fundamentals.reflection.macrortti.{LTag, LightTypeTag, LightTypeTagImpl}

import scala.reflect.runtime.{universe => ru}

// TODO: hotspots, hashcode on keys is inefficient
@deprecated("removing now ???", "0.10")
class SafeType0[U <: SingletonUniverse] protected(
                                                   private val tpe: U#Type,
                                                   val tag: LightTypeTag,
                                                 ) {
  override final val hashCode: Int = {
    tag.hashCode()
  }

  override final lazy val toString: String = {
    tag.repr
  }

  override final def equals(obj: Any): Boolean = {
    obj match {
      case that: SafeType0[U]@unchecked =>
        tag =:= that.tag
      case _ =>
        false
    }
  }

  final def =:=(that: SafeType0[U]): Boolean = {
    tag =:= that.tag
  }

  final def <:<(that: SafeType0[U]): Boolean = {
    tag <:< that.tag
  }

  @deprecated("Avoid using runtime reflection, this will be removed in future", "0.9.0")
  def use[T](f: U#Type => T): T = {
    ReflectionLock.synchronized {
      f(tpe)
    }
  }
}

object SafeType0 {
  @deprecated("constructing SafeType from a runtime type tag", "0.9.0")
  def apply(tpe: ru.Type): SafeType0[ru.type] = {
    new SafeType0[ru.type](tpe, LightTypeTagImpl.makeLightTypeTag(ru)(tpe))
  }

  def get[T: ru.TypeTag : LTag.Weak]: SafeType0[ru.type] = new SafeType0[ru.type](ru.typeOf[T], LTag.Weak[T].tag)
}
