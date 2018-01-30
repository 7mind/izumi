package com.github.pshirshov.izumi.distage.model.plan

import com.github.pshirshov.izumi.distage.model.definition.ContextDefinition

case class FinalPlanImmutableImpl
(override val definition: ContextDefinition)
(override val steps: Seq[ExecutableOp])
  extends FinalPlan
