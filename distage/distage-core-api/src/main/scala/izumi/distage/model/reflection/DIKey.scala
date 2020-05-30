package izumi.distage.model.reflection

import izumi.distage.model.definition.{ContractedId, ImplDef}
import izumi.reflect.Tag

sealed trait DIKey {
  def tpe: SafeType
  override final lazy val hashCode: Int = scala.util.hashing.MurmurHash3.productHash(this.asInstanceOf[Product])
  protected def formatWithIndex(base: String, index: Option[Int]): String = {
    index match {
      case Some(value) =>
        s"$base::$value"
      case None =>
        base
    }
  }
}

object DIKey {
  def apply[T: Tag]: DIKey = TypeKey(SafeType.get[T])
  def apply[T: Tag](id: String): DIKey = DIKey.get[T].named(id)

  def get[T: Tag]: DIKey.TypeKey = DIKey.TypeKey(SafeType.get[T])

  sealed trait BasicKey extends DIKey {
    def withTpe(tpe: SafeType): DIKey.BasicKey
  }

  final case class TypeKey(tpe: SafeType, mutatorIndex: Option[Int] = None) extends BasicKey {
    def named[I: IdContract](id: I): IdKey[I] = IdKey(tpe, id, mutatorIndex)
    def named[I](contractedId: ContractedId[I]): IdKey[I] = IdKey.fromContractedId(tpe, contractedId, mutatorIndex)

    override def withTpe(tpe: SafeType): DIKey.TypeKey = copy(tpe = tpe)
    override def toString: String = formatWithIndex(s"{type.${tpe.toString}}", mutatorIndex)
  }

  final case class IdKey[I: IdContract](tpe: SafeType, id: I, mutatorIndex: Option[Int] = None) extends BasicKey {
    val idContract: IdContract[I] = implicitly
    def withMutatorIndex(index: Option[Int]): IdKey[I] = copy(mutatorIndex = index)
    override def withTpe(tpe: SafeType): DIKey.IdKey[I] = copy(tpe = tpe)
    override def toString: String = formatWithIndex(s"{type.${tpe.toString}@${idContract.repr(id)}}", mutatorIndex)
  }

  object IdKey {
    def fromContractedId[I](tpe: SafeType, contractedId: ContractedId[I], mutatorIndex: Option[Int] = None): IdKey[I] = {
      IdKey(tpe, contractedId.id, mutatorIndex)(contractedId.contract)
    }
  }

  /**
    * @param set       Key of the parent Set. `set.tpe` must be of type `Set[T]`
    * @param reference Key of `this` individual element. `reference.tpe` must be a subtype of `T`
    */
  final case class SetElementKey(set: DIKey, reference: DIKey, disambiguator: Option[ImplDef]) extends DIKey {
    override def tpe: SafeType = reference.tpe

    override def toString: String = s"{set.$set/${reference.toString}#${disambiguator.fold("0")(_.hashCode.toString)}"
  }

  final case class ProxyElementKey(proxied: DIKey, tpe: SafeType) extends DIKey {
    override def toString: String = s"{proxy.${proxied.toString}}"
  }

  final case class ResourceKey(key: DIKey, tpe: SafeType) extends DIKey {
    override def toString: String = s"{resource.${key.toString}/$tpe}"
  }

  final case class EffectKey(key: DIKey, tpe: SafeType) extends DIKey {
    override def toString: String = s"{effect.${key.toString}/$tpe}"
  }

  final case class MultiSetImplId(set: DIKey, impl: ImplDef)
  object MultiSetImplId {
    implicit object SetImplIdContract extends IdContract[MultiSetImplId] {
      override def repr(v: MultiSetImplId): String = s"set/${v.set}#${v.impl.hashCode}"
    }
  }
}
