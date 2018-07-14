package com.github.pshirshov.izumi.distage.model.plan

import com.github.pshirshov.izumi.distage.model.definition.ModuleBase

case class ReplanningContext(count: Int, module: ModuleBase, source: DodgyPlan, previous: Option[FinalPlan])
