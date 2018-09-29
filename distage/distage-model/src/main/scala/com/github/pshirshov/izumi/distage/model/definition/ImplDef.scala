package com.github.pshirshov.izumi.distage.model.definition

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse

sealed trait ImplDef {
  def implType: RuntimeDIUniverse.SafeType
}

object ImplDef {
  final case class ReferenceImpl(implType: RuntimeDIUniverse.SafeType, key: RuntimeDIUniverse.DIKey, weak: Boolean) extends ImplDef

  final case class TypeImpl(implType: RuntimeDIUniverse.SafeType) extends ImplDef

  final case class InstanceImpl(implType: RuntimeDIUniverse.SafeType, instance: Any) extends ImplDef

  final case class ProviderImpl(implType: RuntimeDIUniverse.SafeType, function: RuntimeDIUniverse.Provider) extends ImplDef
}
