package com.github.pshirshov.izumi.distage.model.planning

import com.github.pshirshov.izumi.distage.model.plan.ReplanningContext
import com.github.pshirshov.izumi.functional.Value

trait ReplanningCriterion {
  def shouldReplan(context: ReplanningContext, afterResolveHook: Value[ExtendedFinalPlan], finalPlan: ExtendedFinalPlan): Boolean
}
