package izumi.distage.model.plan

import izumi.distage.model.GCMode
import izumi.distage.model.definition.ModuleBase
import izumi.distage.model.plan.ExecutableOp.SemiplanOp
import izumi.distage.model.plan.impl.{AbstractPlanOps, OrderedPlanExtensions, OrderedPlanOps, PlanLazyOps, SemiPlanExtensions, SemiPlanOps}
import izumi.distage.model.plan.topology.PlanTopology
import izumi.distage.model.reflection.universe.RuntimeDIUniverse


sealed trait AbstractPlan[OpType <: ExecutableOp] extends AbstractPlanExtendedAPI[OpType] {
  def definition: ModuleBase

  def gcMode: GCMode

  def steps: Seq[OpType]

  def index: Map[RuntimeDIUniverse.DIKey, OpType]

  override def toString: String = {
    steps.map(_.toString).mkString("\n")
  }
}


object AbstractPlan extends AbstractPlanOps

sealed trait ExtendedPlan[OpType <: ExecutableOp] extends AbstractPlan[OpType] with PlanLazyOps[OpType]

/**
  * An unordered plan.
  *
  * You can turn into an [[OrderedPlan]] via [[izumi.distage.model.Planner.finish]]
  */
final case class SemiPlan(
                           steps: Vector[SemiplanOp],
                           gcMode: GCMode
                         ) extends ExtendedPlan[SemiplanOp] with SemiPlanOps

object SemiPlan extends SemiPlanExtensions

/**
  * Linearized graph which is ready to be consumed by linear executors
  *
  * May contain cyclic dependencies resolved with proxies
  */
final case class OrderedPlan(
                              steps: Vector[ExecutableOp],
                              gcMode: GCMode,
                              topology: PlanTopology
                            ) extends ExtendedPlan[ExecutableOp] with OrderedPlanOps


object OrderedPlan extends OrderedPlanExtensions



