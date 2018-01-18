package org.bitbucket.pshirshov.izumi.di.model.plan

import org.bitbucket.pshirshov.izumi.di.definition.ContextDefinition

trait FinalPlan {
  def definition: ContextDefinition
  def steps: Seq[ExecutableOp]

  override def toString: String = {
    steps.map(_.format).mkString("\n")
  }
}



