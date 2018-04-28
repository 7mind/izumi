package com.github.pshirshov.izumi.distage.model.plan

import com.github.pshirshov.izumi.distage.model.definition.ModuleDef

case class FinalPlanImmutableImpl
(override val definition: ModuleDef)
(override val steps: Seq[ExecutableOp])
  extends FinalPlan
