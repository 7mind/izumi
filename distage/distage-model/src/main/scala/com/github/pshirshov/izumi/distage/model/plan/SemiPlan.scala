package com.github.pshirshov.izumi.distage.model.plan

import com.github.pshirshov.izumi.distage.model.Locator
import com.github.pshirshov.izumi.distage.model.definition.ModuleBase
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.ImportDependency
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.WiringOp.ReferenceInstance
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.Wiring.UnaryWiring.Instance

sealed trait AbstractPlan {
  def definition: ModuleBase
  def steps: Seq[ExecutableOp]

  lazy val index: Map[RuntimeDIUniverse.DIKey, ExecutableOp] = {
    steps.map(s => s.target -> s).toMap
  }

  def map(f: ExecutableOp => ExecutableOp): SemiPlan

  def flatMap(f: ExecutableOp => Seq[ExecutableOp]): SemiPlan

  def collect(f: PartialFunction[ExecutableOp, ExecutableOp]): SemiPlan

  def foldLeft[T](z: T, f: (T, ExecutableOp) => T): T = {
    steps.foldLeft(z)(f)
  }

  def resolveImports(f: PartialFunction[ImportDependency, Any]): AbstractPlan

  def locateImports(locator: Locator): AbstractPlan =
    resolveImports(Function.unlift {
      i => locator.lookup[Any](i.target)
    })

  override def toString: String = {
    steps.map(_.format).mkString("\n")
  }
}

object AbstractPlan {
  private[plan] def resolveImports(f: PartialFunction[ImportDependency, Any], steps: Vector[ExecutableOp]): Vector[ExecutableOp] =
    steps.map {
      case i: ImportDependency =>
        f.lift(i).fold[ExecutableOp](i)(instance => ReferenceInstance(i.target, Instance(i.target.tpe, instance), i.origin))
      case op =>
        op
    }
}

/** Unordered plan. You can turn into an [[OrderedPlan]] by using [[com.github.pshirshov.izumi.distage.model.Planner#order]] **/
final case class SemiPlan(definition: ModuleBase, steps: Vector[ExecutableOp]) extends AbstractPlan {
  def map(f: ExecutableOp => ExecutableOp): SemiPlan = {
    copy(steps = steps.map(f))
  }

  def flatMap(f: ExecutableOp => Seq[ExecutableOp]): SemiPlan = {
    copy(steps = steps.flatMap(f))
  }

  def collect(f: PartialFunction[ExecutableOp, ExecutableOp]): SemiPlan = {
    copy(steps = steps.collect(f))
  }

  def resolveImports(f: PartialFunction[ImportDependency, Any]): SemiPlan = {
    copy(steps = AbstractPlan.resolveImports(f, steps))
  }
}

final case class OrderedPlan(definition: ModuleBase, steps: Vector[ExecutableOp], topology: PlanTopology) extends AbstractPlan {
  def map(f: ExecutableOp => ExecutableOp): SemiPlan = {
    SemiPlan(definition, steps.map(f))
  }

  def flatMap(f: ExecutableOp => Seq[ExecutableOp]): SemiPlan = {
    SemiPlan(definition, steps.flatMap(f))
  }

  def collect(f: PartialFunction[ExecutableOp, ExecutableOp]): SemiPlan = {
    SemiPlan(definition, steps.collect(f))
  }

  def resolveImports(f: PartialFunction[ImportDependency, Any]): OrderedPlan = {
    copy(steps = AbstractPlan.resolveImports(f, steps))
  }
}
