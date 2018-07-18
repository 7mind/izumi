package com.github.pshirshov.izumi.distage.model.plan

import com.github.pshirshov.izumi.distage.model.definition.ModuleBase
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse

final case class FinalPlan(definition: ModuleBase, steps: Seq[ExecutableOp]) {

  def index: Map[RuntimeDIUniverse.DIKey, ExecutableOp] = {
    steps.map(s => s.target -> s).toMap
  }

  def map(f: ExecutableOp => ExecutableOp): FinalPlan = {
    copy(steps = steps.map(f))
  }

  def flatMap(f: ExecutableOp => Seq[ExecutableOp]): FinalPlan = {
    copy(steps = steps.flatMap(f))
  }

  def foldLeft[T](z: T, f: (T, ExecutableOp) => T): T = {
    steps.foldLeft(z)(f)
  }

  override def toString: String = {
    steps.map(_.format).mkString("\n")
  }
}



