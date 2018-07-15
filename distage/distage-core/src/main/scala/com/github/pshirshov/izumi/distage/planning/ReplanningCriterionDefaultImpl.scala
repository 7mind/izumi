package com.github.pshirshov.izumi.distage.planning

import com.github.pshirshov.izumi.distage.model.exceptions.ReplanningLimitReached
import com.github.pshirshov.izumi.distage.model.plan.ReplanningContext
import com.github.pshirshov.izumi.distage.model.planning.{ExtendedFinalPlan, ReplanningCriterion}
import com.github.pshirshov.izumi.functional.Value

class ReplanningCriterionDefaultImpl extends ReplanningCriterion {
  override def shouldReplan(context: ReplanningContext, afterResolveHook: Value[ExtendedFinalPlan], finalPlan: ExtendedFinalPlan): Boolean = {
    if (context.count > 5) {
      throw new ReplanningLimitReached(
        s"""Replanning limit reached, giving up.
           |Note:
           |(1) replanning is a last-chance feature, use it carefully
           |(2) replanning happens only in case it has been explicitly requested by a hook
         """.stripMargin, context)

    }

    afterResolveHook.get.replanningRequested || finalPlan.replanningRequested
  }
}
