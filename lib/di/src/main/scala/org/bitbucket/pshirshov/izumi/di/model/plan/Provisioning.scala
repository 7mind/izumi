package org.bitbucket.pshirshov.izumi.di.model.plan

import org.bitbucket.pshirshov.izumi.di.definition.ImplDef

sealed trait Provisioning

object Provisioning {
  case class Possible(ops: Seq[ExecutableOp]) extends Provisioning
  case class Impossible(implDef: ImplDef) extends Provisioning
}