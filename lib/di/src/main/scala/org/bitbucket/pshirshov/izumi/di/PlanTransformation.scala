package org.bitbucket.pshirshov.izumi.di

import org.bitbucket.pshirshov.izumi.di.plan.Op

sealed trait PlanTransformation

object PlanTransformation {

  case class Leave(op: Op) extends PlanTransformation

  case class Replace(op: Op, replacement: Op) extends PlanTransformation

  case class UnsolvableConflict(op: Op, existing: Op) extends PlanTransformation

  case class Duplicated(op: Op) extends PlanTransformation
}