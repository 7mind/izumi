package com.github.pshirshov.izumi.distage

import com.github.pshirshov.izumi.distage.definition.ContextDefinition
import com.github.pshirshov.izumi.distage.model.plan._


trait Planner {
  def plan(context: ContextDefinition): FinalPlan
}







