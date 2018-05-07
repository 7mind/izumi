package com.github.pshirshov.izumi.distage.model.plan

import com.github.pshirshov.izumi.distage.model.definition.ModuleBase

final case class FinalPlanImmutableImpl(
                                   definition: ModuleBase
                                   , steps: Seq[ExecutableOp]
                                 ) extends FinalPlan
