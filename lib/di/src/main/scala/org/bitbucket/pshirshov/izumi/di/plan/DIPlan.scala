package org.bitbucket.pshirshov.izumi.di.plan

import org.bitbucket.pshirshov.izumi.di.model.DIKey

trait DIPlan {
  def getPlan: Seq[Op]
  def contains(dependency: DIKey): Boolean

  override def toString: String = {
    getPlan.map(_.format).mkString("\n")
  }
}

class ImmutablePlan(ops: Seq[Op]) extends DIPlan {
  //private val
  override def getPlan: Seq[Op] = ops

  override def contains(dependency: DIKey): Boolean = {
    ops.exists(_.target == dependency)
  }
}

object DIPlan {
  def empty: DIPlan = new ImmutablePlan(Seq.empty)
}