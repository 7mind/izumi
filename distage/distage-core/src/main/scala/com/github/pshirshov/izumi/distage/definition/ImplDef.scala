package com.github.pshirshov.izumi.distage.definition

import com.github.pshirshov.izumi.distage.model.Callable
import com.github.pshirshov.izumi.distage.{CustomDef, TypeFull}

sealed trait ImplDef

object ImplDef {
  case class TypeImpl(implType: TypeFull) extends ImplDef

  case class InstanceImpl(implType: TypeFull, instance: Any) extends ImplDef

  case class ProviderImpl(implType: TypeFull, function: Callable) extends ImplDef

  // not sure if it's required though why not have it?..
  case class CustomImpl(data: CustomDef) extends ImplDef
}
