package izumi.fundamentals.reflection

import izumi.fundamentals.reflection.macrortti.{LTag, LightTypeTag, LightTypeTagImpl}

import scala.reflect.runtime.{universe => ru}

// TODO: hotspots, hashcode on keys is inefficient
class SafeType0[U <: SingletonUniverse] protected (
                                                    private val u: U, // Needed just for the corner case in TagTest."work with odd type prefixes" ._.
                                                    val tpe: U#Type,
                                                    protected[reflection] val fullLightTypeTag: LightTypeTag,
) {

  override final val hashCode: Int = {
      fullLightTypeTag.hashCode()
  }

  override final lazy val toString: String = {
      fullLightTypeTag.repr
  }

  override final def equals(obj: Any): Boolean = {
    obj match {
      case that: SafeType0[U] @unchecked =>
          fullLightTypeTag == that.fullLightTypeTag
      case _ =>
        false
    }
  }

  final def =:=(that: SafeType0[U]): Boolean = {
    fullLightTypeTag =:= that.fullLightTypeTag
  }

  final def <:<(that: SafeType0[U]): Boolean = {
      fullLightTypeTag <:< that.fullLightTypeTag
  }

  @deprecated("Weak conformance is useless for DI; weakly conformed numbers are not actually assignable in runtime", "0.9.0")
  final def weak_<:<(that: SafeType0[U]): Boolean = {
      fullLightTypeTag <:< that.fullLightTypeTag
  }

}

object SafeType0 {
  @deprecated("constructing SafeType from a runtime type tag", "0.9.0")
  def apply(tpe: ru.Type): SafeType0[ru.type] = new SafeType0[ru.type](ru, tpe, LightTypeTagImpl.makeFLTT(ru)(tpe))

  def get[T: ru.TypeTag: LTag.Weak]: SafeType0[ru.type] = new SafeType0[ru.type](ru, ru.typeOf[T], LTag.Weak[T].fullLightTypeTag)
}
