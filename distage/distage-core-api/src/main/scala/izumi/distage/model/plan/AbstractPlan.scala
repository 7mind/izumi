package izumi.distage.model.plan

import izumi.distage.model.definition.ModuleBase
import izumi.distage.model.plan.ExecutableOp.SemiplanOp
import izumi.distage.model.plan.impl.{OrderedPlanExtensions, OrderedPlanOps, PlanLazyOps, SemiPlanExtensions, SemiPlanOps}
import izumi.distage.model.plan.topology.PlanTopology
import izumi.distage.model.reflection._

sealed trait AbstractPlan[OpType <: ExecutableOp] extends AbstractPlanExtendedAPI[OpType] with PlanLazyOps[OpType] {
  def definition: ModuleBase
  def steps: Seq[OpType]
  def index: Map[DIKey, OpType]

  /** A long-form rendering of the Plan */
  final def repr: String = steps.iterator.map(_.toString).mkString("\n")
  override def toString: String = repr
}

/**
  * An unordered plan.
  *
  * You can turn into an [[OrderedPlan]] via [[izumi.distage.model.Planner.finish]]
  */
final case class SemiPlan(
                           steps: Vector[SemiplanOp],
                           gcMode: GCMode,
                         ) extends AbstractPlan[SemiplanOp] with SemiPlanOps

object SemiPlan extends SemiPlanExtensions

/**
  * Linearized graph which is ready to be consumed by linear executors
  *
  * May contain cyclic dependencies resolved with proxies
  */
final case class OrderedPlan(
                              steps: Vector[ExecutableOp],
                              declaredRoots: Set[DIKey],
                              topology: PlanTopology,
                            ) extends AbstractPlan[ExecutableOp] with OrderedPlanOps

object OrderedPlan extends OrderedPlanExtensions
