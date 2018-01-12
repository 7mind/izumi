package org.bitbucket.pshirshov.izumi.di

import org.bitbucket.pshirshov.izumi.di.definition.ContextDefinition
import org.bitbucket.pshirshov.izumi.di.model.plan._


trait Planner {
  def plan(context: ContextDefinition): FinalPlan
}







