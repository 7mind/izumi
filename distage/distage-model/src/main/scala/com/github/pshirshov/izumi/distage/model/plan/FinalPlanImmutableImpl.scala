package com.github.pshirshov.izumi.distage.model.plan

import com.github.pshirshov.izumi.distage.model.definition.ModuleDef

case class FinalPlanImmutableImpl(
                                   definition: ModuleDef
                                   , steps: Seq[ExecutableOp]
                                 ) extends FinalPlan
