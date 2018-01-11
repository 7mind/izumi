package org.bitbucket.pshirshov.izumi.di.model.plan

import org.bitbucket.pshirshov.izumi.di.model.DIKey

trait ReadyPlan {
  def steps: Seq[ExecutableOp]
  def contains(dependency: DIKey): Boolean

  override def toString: String = {
    steps.map(_.format).mkString("\n")
  }
}

object ReadyPlan {
  def empty: ReadyPlan = new ReadyPlanImmutableImpl(Seq.empty)
}



