package com.github.pshirshov.izumi.distage.model.definition

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.{DIKey, Provider, SafeType}

sealed trait ImplDef {
  def implType: SafeType
}

object ImplDef {
  sealed trait DirectImplDef extends ImplDef

  final case class ReferenceImpl(implType: SafeType, key: DIKey, weak: Boolean) extends DirectImplDef

  final case class TypeImpl(implType: SafeType) extends DirectImplDef

  final case class InstanceImpl(implType: SafeType, instance: Any) extends DirectImplDef

  final case class ProviderImpl(implType: SafeType, function: Provider) extends DirectImplDef

  sealed trait RecursiveImplDef extends ImplDef

  final case class EffectImpl(implType: SafeType, effectImpl: DirectImplDef) extends RecursiveImplDef

  final case class ResourceImpl(implType: SafeType, resourceImpl: DirectImplDef) extends RecursiveImplDef
}
