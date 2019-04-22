package com.github.pshirshov.izumi.distage.testkit.legacy

import com.github.pshirshov.izumi.distage.model.Locator
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp
import com.github.pshirshov.izumi.distage.roles.RoleComponent
import com.github.pshirshov.izumi.logstage.api.IzLogger

object NullDistageResourceCollection extends DistageResourceCollection {
  override def isMemoized(resource: Any): Boolean = false

  override def transformPlanElement(op: ExecutableOp): ExecutableOp = op

  override def startMemoizedComponents(components: Set[RoleComponent])(implicit logger: IzLogger): Unit = {}

  override def processContext(context: Locator): Unit = {}
}
