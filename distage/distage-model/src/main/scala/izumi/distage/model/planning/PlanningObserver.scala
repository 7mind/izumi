package izumi.distage.model.planning

import izumi.distage.model.plan.initial.PrePlan
import izumi.distage.model.plan.{OrderedPlan, SemiPlan}
import izumi.fundamentals.platform.language.Quirks._

// TODO: Just one method onPhase(Id, plan) ?

/**
  * Execute side-effects to observe planning algorithm execution, e.g. log, write GraphViz files, etc.
  *
  * @see GraphDumpObserver
  */
trait PlanningObserver {
  def onSuccessfulStep(next: PrePlan): Unit = next.discard()

  def onPhase00PlanCompleted(plan: PrePlan): Unit = plan.discard()
  def onPhase05PreGC(plan: SemiPlan): Unit = plan.discard()
  def onPhase10PostGC(plan: SemiPlan): Unit = plan.discard()
  def onPhase20Customization(plan: SemiPlan): Unit = plan.discard()
  def onPhase50PreForwarding(plan: SemiPlan): Unit = plan.discard()
  def onPhase90AfterForwarding(finalPlan: OrderedPlan): Unit = finalPlan.discard()
}

