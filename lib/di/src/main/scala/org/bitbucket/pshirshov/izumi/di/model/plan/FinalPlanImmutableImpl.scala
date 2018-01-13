package org.bitbucket.pshirshov.izumi.di.model.plan

import org.bitbucket.pshirshov.izumi.di.definition.ContextDefinition

class FinalPlanImmutableImpl
(
  override val steps: Seq[ExecutableOp]
  , override val definition: ContextDefinition
) extends FinalPlan