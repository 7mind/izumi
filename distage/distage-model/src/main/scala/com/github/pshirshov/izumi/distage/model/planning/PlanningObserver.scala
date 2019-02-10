package com.github.pshirshov.izumi.distage.model.planning

import com.github.pshirshov.izumi.distage.model.plan.{DodgyPlan, SemiPlan, OrderedPlan}
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks._

// TODO: Just one method onPhase(Id, plan) ?

/**
  * Execute side-effects to observe planning algorithm execution, e.g. log, write GraphViz files, etc.
  *
  * @see GraphDumpObserver
  */
trait PlanningObserver {
  def onSuccessfulStep(next: DodgyPlan): Unit = next.discard()

  def onPhase00PlanCompleted(plan: DodgyPlan): Unit = plan.discard()
  def onPhase05PreGC(plan: SemiPlan): Unit = plan.discard()
  def onPhase10PostGC(plan: SemiPlan): Unit = plan.discard()
  def onPhase20Customization(plan: SemiPlan): Unit = plan.discard()
  def onPhase50PreForwarding(plan: SemiPlan): Unit = plan.discard()
  def onPhase90AfterForwarding(finalPlan: OrderedPlan): Unit = finalPlan.discard()
}

