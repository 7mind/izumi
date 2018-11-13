package com.github.pshirshov.izumi.distage.testkit

import com.github.pshirshov.izumi.distage.model.Locator
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp

trait ResourceCollection {
  def isMemoized(resource: Any): Boolean

  def processContext(context: Locator): Unit

  def transformPlanElement(op: ExecutableOp): ExecutableOp
}
