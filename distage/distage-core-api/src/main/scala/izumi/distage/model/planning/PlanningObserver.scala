package izumi.distage.model.planning

import izumi.distage.model.plan.{OrderedPlan, SemiPlan}
import izumi.fundamentals.platform.language.unused

/**
  * Execute side-effects to observe planning algorithm execution, e.g. log, write GraphViz files, etc.
  *
  * @see GraphDumpObserver
  */
trait PlanningObserver {
  def onPhase05PreGC(@unused plan: SemiPlan): Unit = {}
  def onPhase10PostGC(@unused plan: SemiPlan): Unit = {}
  def onPhase90AfterForwarding(@unused finalPlan: OrderedPlan): Unit = {}
}
