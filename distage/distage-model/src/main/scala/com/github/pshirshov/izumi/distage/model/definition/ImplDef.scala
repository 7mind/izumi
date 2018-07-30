package com.github.pshirshov.izumi.distage.model.definition

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse

sealed trait ImplDef

object ImplDef {
  sealed trait WithImplType extends ImplDef {
    def implType: RuntimeDIUniverse.SafeType
  }

  final case class ReferenceImpl(implType: RuntimeDIUniverse.SafeType, key: RuntimeDIUniverse.DIKey, weak: Boolean) extends WithImplType

  final case class TypeImpl(implType: RuntimeDIUniverse.SafeType) extends WithImplType

  final case class InstanceImpl(implType: RuntimeDIUniverse.SafeType, instance: Any) extends WithImplType

  final case class ProviderImpl(implType: RuntimeDIUniverse.SafeType, function: RuntimeDIUniverse.Provider) extends WithImplType
}
