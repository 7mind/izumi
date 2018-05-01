package com.github.pshirshov.izumi.distage.model.definition

import com.github.pshirshov.izumi.distage.CustomDef
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse

sealed trait ImplDef

object ImplDef {
  sealed trait WithImplType extends ImplDef {
    def implType: RuntimeDIUniverse.TypeFull
  }
  final case class TypeImpl(implType: RuntimeDIUniverse.TypeFull) extends WithImplType

  final case class InstanceImpl(implType: RuntimeDIUniverse.TypeFull, instance: Any) extends WithImplType

  final case class ProviderImpl(implType: RuntimeDIUniverse.TypeFull, function: RuntimeDIUniverse.Provider) extends WithImplType

  // not sure if it's required though why not have it?..
  final case class CustomImpl(data: CustomDef) extends ImplDef
}
