package izumi.distage.model.reflection.universe

import izumi.distage.model.definition.ImplDef

private[distage] trait WithDIKey { this: DIUniverseBase with WithDISafeType =>

  sealed trait DIKey {
    def tpe: SafeType
  }

  object DIKey {
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

    /**
      * @param set Key of the parent Set. `set.tpe` must be of type `Set[T]`
      * @param reference Key of `this` individual element. `reference.tpe` must be a subtype of `T`
      */
    case class SetElementKey(set: DIKey, reference: DIKey, disambiguator: Option[ImplDef]) extends DIKey {
      override final def tpe: SafeType = reference.tpe

      override final def toString: String = s"{set.$set/${reference.toString}#${disambiguator.fold("0")(_.hashCode.toString)}"
    }
  }

  trait IdContractApi[T] {
    def repr(v: T): String
  }
  type IdContract[T] <: IdContractApi[T]

  implicit def stringIdContract: IdContract[String]
  implicit def singletonStringIdContract[S <: String with Singleton]: IdContract[S]
}
