package com.github.pshirshov.izumi.distage.model.provisioning

import com.github.pshirshov.izumi.distage.model.monadic.DIMonad
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.TagK

trait OperationExecutor {
  def execute[F[_]: TagK: DIMonad](context: ProvisioningKeyProvider, step: ExecutableOp): F[Seq[NewObjectOp]]
}

trait WiringExecutor {
  def execute(context: ProvisioningKeyProvider, step: ExecutableOp.WiringOp): Seq[NewObjectOp]
}
