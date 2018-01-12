package org.bitbucket.pshirshov.izumi.di.model.plan

import org.bitbucket.pshirshov.izumi.di.definition.ContextDefinition
import org.bitbucket.pshirshov.izumi.di.model.DIKey

class FinalPlanImmutableImpl
(
  ops: Seq[ExecutableOp]
  , override val definition: ContextDefinition
) extends FinalPlan {
  override def steps: Seq[ExecutableOp] = ops

  override def contains(dependency: DIKey): Boolean = {
    ops.exists(_.target == dependency)
  }
}
