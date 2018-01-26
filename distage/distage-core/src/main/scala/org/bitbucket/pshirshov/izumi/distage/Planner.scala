package org.bitbucket.pshirshov.izumi.distage

import org.bitbucket.pshirshov.izumi.distage.definition.ContextDefinition
import org.bitbucket.pshirshov.izumi.distage.model.plan._


trait Planner {
  def plan(context: ContextDefinition): FinalPlan
}







