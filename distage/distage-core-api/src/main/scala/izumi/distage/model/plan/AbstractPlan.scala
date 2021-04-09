package izumi.distage.model.plan

import izumi.distage.model.PlannerInput
import izumi.distage.model.definition.ModuleBase
import izumi.distage.model.plan.ExecutableOp.SemiplanOp
import izumi.distage.model.plan.impl.{OrderedPlanExtensions, OrderedPlanOps, PlanLazyOps, SemiPlanExtensions, SemiPlanOps}
import izumi.distage.model.plan.topology.PlanTopology
import izumi.distage.model.reflection._
import izumi.fundamentals.graphs.DG

sealed trait AbstractPlan[OpType <: ExecutableOp] extends AbstractPlanExtendedAPI[OpType] with PlanLazyOps[OpType] {
  def definition: ModuleBase
  def steps: Seq[OpType]
  def index: Map[DIKey, OpType]

  /** A longer-form rendering of the Plan, compared to [[izumi.distage.model.plan.impl.OrderedPlanExtensions.OrderedPlanRenderOps]] */
  final def repr: String = steps.iterator.map(_.toString).mkString("\n", "\n", "")
}

/**
  * An unordered plan.
  */
final case class SemiPlan(
  steps: Vector[SemiplanOp],
  roots: Roots,
) extends AbstractPlan[SemiplanOp]
  with SemiPlanOps {
  override def toString: String = repr
}

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
) extends AbstractPlan[ExecutableOp]
  with OrderedPlanOps {
  /** Print while omitting package names for unambiguous types */
  override def toString: String = "\n" + (this: OrderedPlan).render()
}

object OrderedPlan extends OrderedPlanExtensions
