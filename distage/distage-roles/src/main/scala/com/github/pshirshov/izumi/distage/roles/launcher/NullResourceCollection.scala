package com.github.pshirshov.izumi.distage.roles.launcher

import java.util.concurrent.ExecutorService

import com.github.pshirshov.izumi.distage.model.Locator
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp
import com.github.pshirshov.izumi.distage.roles.roles.{ResourceCollection, RoleComponent}

class NullResourceCollection extends ResourceCollection {
  override def startMemoizedComponents(): Unit = ()

  override def getCloseables: Set[AutoCloseable] = Set.empty

  override def getComponents: Set[RoleComponent] = Set.empty

  override def getExecutors: Set[ExecutorService] = Set.empty

  override def transformPlanElement(op: ExecutableOp): ExecutableOp = op

  override def close(closeable: AutoCloseable): Unit = closeable.close()

  override def processContext(context: Locator): Unit = {}
}
