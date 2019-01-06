package com.github.pshirshov.izumi.distage.testkit

import com.github.pshirshov.izumi.distage.model.Locator
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp
import com.github.pshirshov.izumi.distage.roles.RoleComponent
import com.github.pshirshov.izumi.logstage.api.IzLogger

trait DistageResourceCollection {
  def isMemoized(resource: Any): Boolean

  def startMemoizedComponents(components: Set[RoleComponent])(implicit logger: IzLogger): Unit

  def processContext(context: Locator): Unit

  def transformPlanElement(op: ExecutableOp): ExecutableOp
}
