package com.github.pshirshov.izumi.distage.model.planning

import com.github.pshirshov.izumi.distage.model.plan.{DodgyPlan, SemiPlan, OrderedPlan}

// TODO: Just one method onPhase(Id, plan) ?

/**
  * Execute side-effects to observe planning algorithm execution, e.g. log, write GraphViz files, etc.
  *
  * @see GraphDumpObserver
  */
trait PlanningObserver {
  def onSuccessfulStep(next: DodgyPlan): Unit = {}

  def onPhase00PlanCompleted(plan: DodgyPlan): Unit = {}
  def onPhase05PreGC(plan: SemiPlan): Unit = {}
  def onPhase10PostGC(plan: SemiPlan): Unit = {}
  def onPhase20Customization(plan: SemiPlan): Unit = {}
  def onPhase50PreForwarding(plan: SemiPlan): Unit = {}
  def onPhase90AfterForwarding(finalPlan: OrderedPlan): Unit = {}
}

