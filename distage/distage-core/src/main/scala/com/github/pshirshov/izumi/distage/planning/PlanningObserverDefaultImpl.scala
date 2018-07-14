package com.github.pshirshov.izumi.distage.planning

import com.github.pshirshov.izumi.distage.model.plan.{DodgyPlan, FinalPlan, ReplanningContext, ResolvedCyclesPlan}
import com.github.pshirshov.izumi.distage.model.planning.PlanningObserver
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks

class PlanningObserverDefaultImpl extends PlanningObserver {
  override def onSuccessfulStep(next: DodgyPlan): Unit = {
    Quirks.discard(next)
  }

  override def onReferencesResolved(context: ReplanningContext, plan: ResolvedCyclesPlan): Unit = {
    Quirks.discard(context, plan)
  }

  override def onResolvingFinished(context: ReplanningContext, plan: FinalPlan): Unit = {
    Quirks.discard(context, plan)
  }

  override def onFinalPlan(context: ReplanningContext, finalPlan: FinalPlan): Unit = {
    Quirks.discard(context, finalPlan)
  }
}

