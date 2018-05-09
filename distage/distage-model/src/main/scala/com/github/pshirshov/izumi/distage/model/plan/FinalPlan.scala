package com.github.pshirshov.izumi.distage.model.plan

import com.github.pshirshov.izumi.distage.model.definition.ModuleBase
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse

trait FinalPlan {
  def definition: ModuleBase
  def steps: Seq[ExecutableOp]

  def index: Map[RuntimeDIUniverse.DIKey, ExecutableOp] = {
    steps.map(s => s.target -> s).toMap
  }

  final def flatMap(f: ExecutableOp => Seq[ExecutableOp]): FinalPlan = {
    FinalPlanImmutableImpl(definition, steps.flatMap(f))
  }

  override def toString: String = {
    steps.map(_.format).mkString("\n")
  }
}



