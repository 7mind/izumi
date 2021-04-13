package izumi.distage.model.plan

import izumi.distage.model.PlannerInput
import izumi.distage.model.definition.{Activation, ModuleBase}
import izumi.distage.model.plan.impl._
import izumi.distage.model.plan.topology.PlanTopology
import izumi.distage.model.reflection._
import izumi.fundamentals.graphs.struct.IncidenceMatrix
import izumi.fundamentals.graphs.{DG, GraphMeta}

sealed trait AbstractPlan[OpType <: ExecutableOp] extends AbstractPlanExtendedAPI[OpType] with PlanLazyOps[OpType] {
  def definition: ModuleBase

  def steps: Seq[OpType]

  def index: Map[DIKey, OpType]

  /** A longer-form rendering of the Plan, compared to [[izumi.distage.model.plan.impl.OrderedPlanExtensions.OrderedPlanRenderOps]] */
  final def repr: String = steps.iterator.map(_.toString).mkString("\n", "\n", "")
}

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
  @deprecated("should be removed with OrderedPlan", "13/04/2021")
  def toDIPlan: DIPlan = DIPlan(
    DG(
      IncidenceMatrix(topology.dependencies.graph),
      IncidenceMatrix(topology.dependees.graph),
      GraphMeta(steps.map(e => (e.target, e)).toMap),
    ),
    PlannerInput(
      izumi.distage.model.definition.Module.empty,
      Activation.empty,
      Roots.Everything,
    ),
  )

  /** Print while omitting package names for unambiguous types */
  override def toString: String = "\n" + (this: OrderedPlan).render()
}

object OrderedPlan extends OrderedPlanExtensions
