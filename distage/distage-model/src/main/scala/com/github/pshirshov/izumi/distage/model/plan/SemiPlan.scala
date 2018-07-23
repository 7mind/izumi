package com.github.pshirshov.izumi.distage.model.plan

import com.github.pshirshov.izumi.distage.model.definition.ModuleBase
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse

sealed trait AbstractPlan {
  def steps: Seq[ExecutableOp]

  lazy val index: Map[RuntimeDIUniverse.DIKey, ExecutableOp] = {
    steps.map(s => s.target -> s).toMap
  }

  def foldLeft[T](z: T, f: (T, ExecutableOp) => T): T = {
    steps.foldLeft(z)(f)
  }

  override def toString: String = {
    steps.map(_.format).mkString("\n")
  }
}

final case class SemiPlan(definition: ModuleBase, steps: Vector[ExecutableOp]) extends AbstractPlan {
  def map(f: ExecutableOp => ExecutableOp): SemiPlan = {
    copy(steps = steps.map(f))
  }

  def flatMap(f: ExecutableOp => Seq[ExecutableOp]): SemiPlan = {
    copy(steps = steps.flatMap(f))
  }
}

final case class OrderedPlan(definition: ModuleBase, steps: Vector[ExecutableOp], topology: PlanTopology) extends AbstractPlan {
  def map(f: ExecutableOp => ExecutableOp): OrderedPlan = {
    copy(steps = steps.map(f))
  }

  def flatMap(f: ExecutableOp => Seq[ExecutableOp]): OrderedPlan = {
    copy(steps = steps.flatMap(f))
  }
}
