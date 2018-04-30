package com.github.pshirshov.izumi.distage.model.plan

import com.github.pshirshov.izumi.distage.model.definition.AbstractModuleDef

case class FinalPlanImmutableImpl
(override val definition: AbstractModuleDef)
(override val steps: Seq[ExecutableOp])
  extends FinalPlan
