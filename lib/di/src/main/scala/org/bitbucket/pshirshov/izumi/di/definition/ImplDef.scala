package org.bitbucket.pshirshov.izumi.di.definition

import org.bitbucket.pshirshov.izumi.di.Symb

sealed trait ImplDef

object ImplDef {
  case class TypeImpl(impl: Symb) extends ImplDef
  case class InstanceImpl(instance: AnyRef) extends ImplDef
}