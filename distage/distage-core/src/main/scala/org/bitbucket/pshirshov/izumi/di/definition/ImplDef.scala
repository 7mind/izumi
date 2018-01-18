package org.bitbucket.pshirshov.izumi.di.definition

import org.bitbucket.pshirshov.izumi.di.model.Callable
import org.bitbucket.pshirshov.izumi.di.{CustomDef, TypeFull}

sealed trait ImplDef

object ImplDef {
  case class TypeImpl(implType: TypeFull) extends ImplDef

  case class InstanceImpl(implType: TypeFull, instance: Any) extends ImplDef

  case class ProviderImpl(implType: TypeFull, function: Callable) extends ImplDef

  // not sure if it's required though why not have it?..
  case class CustomImpl(data: CustomDef) extends ImplDef
}
