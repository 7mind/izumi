package com.github.pshirshov.izumi.distage.model

import com.github.pshirshov.izumi.distage.model.definition.AbstractModuleDef
import com.github.pshirshov.izumi.distage.model.plan._


trait Planner {
  def plan(context: AbstractModuleDef): FinalPlan
}







