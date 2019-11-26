package izumi.distage.model.plan

import izumi.distage.model.plan.ExecutableOp.{ImportDependency, SemiplanOp}
import izumi.distage.model.plan.ExecutableOp.WiringOp.ReferenceInstance
import izumi.distage.model.reflection.universe.RuntimeDIUniverse.Wiring.SingletonWiring.Instance


trait AbstractPlanOps {
  private[plan] def resolveImports[OpType <: ExecutableOp](f: PartialFunction[ImportDependency, Seq[OpType]], steps: Vector[OpType]): Vector[ExecutableOp] =
    steps.flatMap {
      case i: ImportDependency =>
        f.lift(i) getOrElse Seq(i)
      case op =>
        Seq(op)
    }

  private[plan] def resolveImports1(f: PartialFunction[ImportDependency, Seq[SemiplanOp]], steps: Vector[SemiplanOp]): Vector[SemiplanOp] =
    steps.flatMap {
      case i: ImportDependency =>
        f.lift(i) getOrElse Seq(i)
      case op =>
        Seq(op)
    }


  private[plan] def importToInstances(f: PartialFunction[ImportDependency, Any]): PartialFunction[ImportDependency, Seq[ExecutableOp.WiringOp]] =
    Function.unlift(i => f.lift(i).map(instance => Seq(ReferenceInstance(i.target, Instance(i.target.tpe, instance), i.origin))))

}
