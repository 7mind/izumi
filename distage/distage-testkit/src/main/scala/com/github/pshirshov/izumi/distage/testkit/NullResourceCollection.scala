package com.github.pshirshov.izumi.distage.testkit

import com.github.pshirshov.izumi.distage.model.Locator
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp

class NullResourceCollection extends ResourceCollection {
  override def isMemoized(resource: Any): Boolean = false

  override def transformPlanElement(op: ExecutableOp): ExecutableOp = op

  override def processContext(context: Locator): Unit = {}
}
