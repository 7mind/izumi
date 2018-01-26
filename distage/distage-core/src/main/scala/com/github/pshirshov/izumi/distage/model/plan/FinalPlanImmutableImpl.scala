package com.github.pshirshov.izumi.distage.model.plan

import com.github.pshirshov.izumi.distage.definition.ContextDefinition

class FinalPlanImmutableImpl
(
  override val steps: Seq[ExecutableOp]
  , override val definition: ContextDefinition
) extends FinalPlan