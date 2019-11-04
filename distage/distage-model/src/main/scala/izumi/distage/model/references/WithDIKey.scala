package izumi.distage.model.references

import izumi.distage.model.definition.ImplDef
import izumi.distage.model.reflection.universe.{DIUniverseBase, WithDISafeType, WithTags}

trait WithDIKey {
  this: DIUniverseBase
    with WithDISafeType
    with WithTags =>

  sealed trait DIKey {
    def tpe: SafeType
  }

  object DIKey {
    def get[K: Tag]: TypeKey = TypeKey(SafeType.get[K])

    sealed trait BasicKey extends DIKey {
      def withTpe(tpe: SafeType): DIKey.BasicKey
    }

    case class TypeKey(tpe: SafeType) extends BasicKey {
      override def toString: String = s"{type.${tpe.toString}}"

      final def named[I: IdContract](id: I): IdKey[I] = IdKey(tpe, id)

      override final def withTpe(tpe: SafeType): DIKey.TypeKey = copy(tpe = tpe)
    }

    case class IdKey[I: IdContract](tpe: SafeType, id: I) extends BasicKey {
      final val idContract: IdContract[I] = implicitly

      override def toString: String = s"{type.${tpe.toString}@${idContract.repr(id)}}"

      override final def withTpe(tpe: SafeType): DIKey.IdKey[I] = copy(tpe = tpe)
    }

    case class ProxyElementKey(proxied: DIKey, tpe: SafeType) extends DIKey {
      override def toString: String = s"{proxy.${proxied.toString}}"
    }

    /**
      * @param set Key of the parent Set. `set.tpe` must be of type `Set[T]`
      * @param reference Key of `this` individual element. `reference.tpe` must be a subtype of `T`
      */
    case class SetElementKey(set: DIKey, reference: DIKey, disambiguator: Option[ImplDef]) extends DIKey {
      override def tpe: SafeType = reference.tpe

      override def toString: String = s"{set.$set/${reference.toString}#${disambiguator.fold("0")(_.hashCode().toString)}"
    }

    case class MultiSetImplId(set: DIKey, impl: ImplDef)
    object MultiSetImplId {
      implicit object SetImplIdContract extends IdContract[MultiSetImplId] {
        override def repr(v: MultiSetImplId): String = s"set/${v.set}#${v.impl.hashCode()}"
      }
    }
  }

  trait IdContract[T] {
    def repr(v: T): String
  }

  implicit def stringIdContract: IdContract[String]
  implicit def singletonStringIdContract[S <: String with Singleton]: IdContract[S]
}
