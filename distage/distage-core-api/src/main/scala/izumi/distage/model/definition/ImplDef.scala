package izumi.distage.model.definition

import izumi.distage.model.plan.repr.{BindingFormatter, KeyFormatter}
import izumi.distage.model.reflection.{DIKey, Provider, SafeType}

sealed abstract class ImplDef extends Product {
  def implType: SafeType

  override final def toString: String = BindingFormatter(KeyFormatter.Full).formatImplDef(this)
  override final lazy val hashCode: Int = scala.util.hashing.MurmurHash3.productHash(this)
}

object ImplDef {
  sealed trait DirectImplDef extends ImplDef
  final case class ReferenceImpl(implType: SafeType, key: DIKey, weak: Boolean) extends DirectImplDef
  final case class InstanceImpl(implType: SafeType, instance: Any) extends DirectImplDef
  final case class ProviderImpl(implType: SafeType, function: Provider) extends DirectImplDef

  sealed trait RecursiveImplDef extends ImplDef
  final case class EffectImpl(implType: SafeType, effectHKTypeCtor: SafeType, effectImpl: DirectImplDef) extends RecursiveImplDef
  final case class ResourceImpl(implType: SafeType, effectHKTypeCtor: SafeType, resourceImpl: DirectImplDef) extends RecursiveImplDef
}
