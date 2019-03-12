package com.github.pshirshov.izumi.distage.model.provisioning.strategies

import com.github.pshirshov.izumi.distage.model.monadic.DIEffect
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.ProxyOp
import com.github.pshirshov.izumi.distage.model.provisioning.{NewObjectOp, OperationExecutor, ProvisioningKeyProvider}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.TagK

trait ProxyStrategy {
  def initProxy[F[_]: TagK: DIEffect](context: ProvisioningKeyProvider, executor: OperationExecutor, initProxy: ProxyOp.InitProxy): F[Seq[NewObjectOp]]

  def makeProxy(context: ProvisioningKeyProvider, makeProxy: ProxyOp.MakeProxy): Seq[NewObjectOp]
}

