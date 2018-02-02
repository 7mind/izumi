package com.github.pshirshov.izumi.distage.model.definition

import com.github.pshirshov.izumi.distage.CustomDef
import com.github.pshirshov.izumi.fundamentals.reflection._

sealed trait ImplDef

object ImplDef {
  case class TypeImpl(implType: RuntimeUniverse.TypeFull) extends ImplDef

  case class InstanceImpl(implType: RuntimeUniverse.TypeFull, instance: Any) extends ImplDef

  case class ProviderImpl(implType: RuntimeUniverse.TypeFull, function: RuntimeUniverse.Callable) extends ImplDef

  // not sure if it's required though why not have it?..
  case class CustomImpl(data: CustomDef) extends ImplDef
}
