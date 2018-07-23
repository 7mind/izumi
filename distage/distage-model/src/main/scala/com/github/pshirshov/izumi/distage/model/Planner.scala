package com.github.pshirshov.izumi.distage.model

import com.github.pshirshov.izumi.distage.model.definition.ModuleBase
import com.github.pshirshov.izumi.distage.model.plan._


trait Planner {
  def plan(context: ModuleBase): OrderedPlan
}







