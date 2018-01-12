package org.bitbucket.pshirshov.izumi.di.model.plan

import org.bitbucket.pshirshov.izumi.di.definition.ContextDefinition
import org.bitbucket.pshirshov.izumi.di.model.DIKey

trait FinalPlan {
  def definition: ContextDefinition
  def steps: Seq[ExecutableOp]
  def contains(dependency: DIKey): Boolean

  override def toString: String = {
    steps.map(_.format).mkString("\n")
  }
}



