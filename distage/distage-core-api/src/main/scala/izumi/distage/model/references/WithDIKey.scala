package izumi.distage.model.references

import izumi.distage.model.definition.ImplDef
import izumi.distage.model.reflection.universe.{DIUniverseBase, WithDISafeType}
import izumi.fundamentals.reflection.Tags.Tag

trait WithDIKey {
  this: DIUniverseBase
    with WithDISafeType =>

  sealed trait DIKey {
    def tpe: SafeType
  }

  object DIKey {
    def get[T: Tag]: DIKey.TypeKey = TypeKey(SafeType.get[T])

    sealed trait BasicKey extends DIKey {
      def withTpe(tpe: SafeType): DIKey.BasicKey
    }

    case class TypeKey(tpe: SafeType) extends BasicKey {
      final def named[I: IdContract](id: I): IdKey[I] = IdKey(tpe, id)

      override final def withTpe(tpe: SafeType): DIKey.TypeKey = copy(tpe = tpe)
      override final def toString: String = s"{type.${tpe.toString}}"
    }

    case class IdKey[I: IdContract](tpe: SafeType, id: I) extends BasicKey {
      final val idContract: IdContract[I] = implicitly

      override final def withTpe(tpe: SafeType): DIKey.IdKey[I] = copy(tpe = tpe)
      override final def toString: String = s"{type.${tpe.toString}@${idContract.repr(id)}}"
    }

    case class ProxyElementKey(proxied: DIKey, tpe: SafeType) extends DIKey {
      override final def toString: String = s"{proxy.${proxied.toString}}"
    }

    case class ResourceKey(key: DIKey, tpe: SafeType) extends DIKey {
      override final def toString: String = s"{resource.${key.toString}/$tpe}"
    }

    case class EffectKey(key: DIKey, tpe: SafeType) extends DIKey {
      override final def toString: String = s"{effect.${key.toString}/$tpe}"
    }

    /**
      * @param set Key of the parent Set. `set.tpe` must be of type `Set[T]`
      * @param reference Key of `this` individual element. `reference.tpe` must be a subtype of `T`
      */
    case class SetElementKey(set: DIKey, reference: DIKey, disambiguator: Option[ImplDef]) extends DIKey {
      override final def tpe: SafeType = reference.tpe

      override final def toString: String = s"{set.$set/${reference.toString}#${disambiguator.fold("0")(_.hashCode.toString)}"
    }

    case class MultiSetImplId(set: DIKey, impl: ImplDef)
    object MultiSetImplId {
      implicit object SetImplIdContract extends IdContractApi[MultiSetImplId] {
        override def repr(v: MultiSetImplId): String = s"set/${v.set}#${v.impl.hashCode}"
      }
    }
  }

  trait IdContractApi[T] {
    def repr(v: T): String
  }
  type IdContract[T] <: IdContractApi[T]

  implicit def stringIdContract: IdContract[String]
  implicit def singletonStringIdContract[S <: String with Singleton]: IdContract[S]
}
