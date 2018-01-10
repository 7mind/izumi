package org.bitbucket.pshirshov.izumi.di.model.plan

import org.bitbucket.pshirshov.izumi.di.model.DIKey

trait ReadyPlan {
  def getPlan: Seq[ExecutableOp]
  def contains(dependency: DIKey): Boolean

  override def toString: String = {
    getPlan.map(_.format).mkString("\n")
  }
}

object ReadyPlan {
  def empty: ReadyPlan = new ReadyPlanImmutableImpl(Seq.empty)
}

class ReadyPlanImmutableImpl(ops: Seq[ExecutableOp]) extends ReadyPlan {
  //private val
  override def getPlan: Seq[ExecutableOp] = ops

  override def contains(dependency: DIKey): Boolean = {
    ops.exists(_.target == dependency)
  }
}

