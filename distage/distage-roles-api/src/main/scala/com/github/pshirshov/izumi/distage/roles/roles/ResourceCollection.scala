package com.github.pshirshov.izumi.distage.roles.roles

import java.util.concurrent.ExecutorService

import com.github.pshirshov.izumi.distage.model.Locator
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp

trait ResourceCollection {
  def startMemoizedComponents(): Unit

  def getCloseables: Set[AutoCloseable]

  def getComponents: Set[RoleComponent]

  def getExecutors: Set[ExecutorService]

  def processContext(context: Locator): Unit

  def transformPlanElement(op: ExecutableOp): ExecutableOp

  def close(closeable: AutoCloseable): Unit
}
