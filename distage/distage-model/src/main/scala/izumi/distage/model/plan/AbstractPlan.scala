package izumi.distage.model.plan

import izumi.distage.model.GCMode
import izumi.distage.model.definition.ModuleBase
import izumi.distage.model.plan.ExecutableOp.WiringOp.ReferenceInstance
import izumi.distage.model.plan.ExecutableOp.{ImportDependency, InstantiationOp}
import izumi.distage.model.reflection.universe.RuntimeDIUniverse
import izumi.distage.model.reflection.universe.RuntimeDIUniverse.Wiring.SingletonWiring.Instance


sealed trait AbstractPlan[OpType <: ExecutableOp] extends ExtendedPlanAPI[OpType] {
  def definition: ModuleBase

  def gcMode: GCMode

  def steps: Seq[ExecutableOp]

  def index: Map[RuntimeDIUniverse.DIKey, ExecutableOp]

  override def toString: String = {
    steps.map(_.toString).mkString("\n")
  }
}

object AbstractPlan {
  private[plan] def resolveImports(f: PartialFunction[ImportDependency, Seq[ExecutableOp]], steps: Vector[ExecutableOp]): Vector[ExecutableOp] =
    steps.flatMap {
      case i: ImportDependency =>
        f.lift(i) getOrElse Seq(i)
      case op =>
        Seq(op)
    }

  private[plan] def importToInstances(f: PartialFunction[ImportDependency, Any]): PartialFunction[ImportDependency, Seq[ExecutableOp]] =
    Function.unlift(i => f.lift(i).map(instance => Seq(ReferenceInstance(i.target, Instance(i.target.tpe, instance), i.origin))))
}

sealed trait ExtendedPlan[OpType <: ExecutableOp] extends AbstractPlan[OpType] with WithLazyIndex[OpType]

/**
  * An unordered plan.
  *
  * You can turn into an [[OrderedPlan]] via [[izumi.distage.model.Planner.finish]]
  */
final case class SemiPlan(
                           steps: Vector[ExecutableOp],
                           gcMode: GCMode
                         ) extends ExtendedPlan[InstantiationOp] with SemiPlanOps

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



