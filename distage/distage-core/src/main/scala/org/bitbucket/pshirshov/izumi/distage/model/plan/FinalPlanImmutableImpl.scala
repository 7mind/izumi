package org.bitbucket.pshirshov.izumi.distage.model.plan

import org.bitbucket.pshirshov.izumi.distage.definition.ContextDefinition

class FinalPlanImmutableImpl
(
  override val steps: Seq[ExecutableOp]
  , override val definition: ContextDefinition
) extends FinalPlan