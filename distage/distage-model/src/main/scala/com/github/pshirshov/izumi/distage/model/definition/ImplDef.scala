package com.github.pshirshov.izumi.distage.model.definition

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse

sealed trait ImplDef

object ImplDef {
  sealed trait WithImplType extends ImplDef {
    def implType: RuntimeDIUniverse.TypeFull
  }

  final case class TypeImpl(implType: RuntimeDIUniverse.TypeFull) extends WithImplType

  final case class InstanceImpl(implType: RuntimeDIUniverse.TypeFull, instance: Any) extends WithImplType

  final case class ProviderImpl(implType: RuntimeDIUniverse.TypeFull, function: RuntimeDIUniverse.Provider) extends WithImplType
}
