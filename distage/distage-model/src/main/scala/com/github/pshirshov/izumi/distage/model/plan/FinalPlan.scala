package com.github.pshirshov.izumi.distage.model.plan

import com.github.pshirshov.izumi.distage.model.definition.AbstractModuleDef

trait FinalPlan {
  def definition: AbstractModuleDef
  def steps: Seq[ExecutableOp]

  final def flatMap(f: ExecutableOp => Seq[ExecutableOp]): FinalPlan =
    FinalPlanImmutableImpl(definition, steps.flatMap(f))

  override def toString: String =
    steps.map(_.format).mkString("\n")
}



