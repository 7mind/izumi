package izumi.distage.model.plan.impl

import izumi.distage.model.plan.ExecutableOp
import izumi.distage.model.plan.ExecutableOp.WiringOp.UseInstance
import izumi.distage.model.plan.ExecutableOp.{ImportDependency, SemiplanOp}
import izumi.distage.model.plan.Wiring.SingletonWiring.Instance

private[plan] object AbstractPlanOps {
  def resolveImports[OpType <: ExecutableOp](f: PartialFunction[ImportDependency, Seq[OpType]], steps: Vector[OpType]): Vector[ExecutableOp] =
    steps.flatMap {
      case i: ImportDependency =>
        f.lift(i) getOrElse Seq(i)
      case op =>
        Seq(op)
    }

  def resolveImports1(f: PartialFunction[ImportDependency, Seq[SemiplanOp]], steps: Vector[SemiplanOp]): Vector[SemiplanOp] =
    steps.flatMap {
      case i: ImportDependency =>
        f.lift(i) getOrElse Seq(i)
      case op =>
        Seq(op)
    }

  def importToInstances(f: PartialFunction[ImportDependency, Any]): PartialFunction[ImportDependency, Seq[ExecutableOp.WiringOp]] =
    Function.unlift(i => f.lift(i).map(instance => Seq(UseInstance(i.target, Instance(i.target.tpe, instance), i.origin))))
}
