package org.bitbucket.pshirshov.izumi.di.definition

import org.bitbucket.pshirshov.izumi.di.{CustomDef, TypeFull}

sealed trait ImplDef

object ImplDef {
  case class TypeImpl(impl: TypeFull) extends ImplDef

  case class InstanceImpl(tpe: TypeFull, instance: Any) extends ImplDef

  // not sure if it's required though why not to have it?..
  case class CustomImpl(data: CustomDef) extends ImplDef
}