package org.bitbucket.pshirshov.izumi.di.model.plan

import org.bitbucket.pshirshov.izumi.di.model.DIKey

class ReadyPlanImmutableImpl(ops: Seq[ExecutableOp]) extends ReadyPlan {
  //private val
  override def steps: Seq[ExecutableOp] = ops

  override def contains(dependency: DIKey): Boolean = {
    ops.exists(_.target == dependency)
  }
}
