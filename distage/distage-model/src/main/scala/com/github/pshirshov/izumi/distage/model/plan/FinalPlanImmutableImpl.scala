package com.github.pshirshov.izumi.distage.model.plan

import com.github.pshirshov.izumi.distage.model.definition.AbstractModuleDef

case class FinalPlanImmutableImpl(
                                   definition: AbstractModuleDef
                                   , steps: Seq[ExecutableOp]
                                 ) extends FinalPlan
