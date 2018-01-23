package org.bitbucket.pshirshov.izumi.distage.model.plan

import org.bitbucket.pshirshov.izumi.distage.definition.ContextDefinition

trait FinalPlan {
  def definition: ContextDefinition
  def steps: Seq[ExecutableOp]

  def flatMap(f: ExecutableOp => Seq[ExecutableOp]): FinalPlan = {
    new FinalPlanImmutableImpl(steps.flatMap(f), definition)
  }

  override def toString: String = {
    steps.map(_.format).mkString("\n")
  }
}



