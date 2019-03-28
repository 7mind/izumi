package com.github.pshirshov.izumi.distage.model.provisioning.strategies

import com.github.pshirshov.izumi.distage.model.monadic.DIEffect
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.MonadicOp
import com.github.pshirshov.izumi.distage.model.provisioning.{NewObjectOp, OperationExecutor, ProvisioningKeyProvider}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.TagK

trait ResourceStrategy {
  def allocateResource[F[_]: TagK: DIEffect](
                                              context: ProvisioningKeyProvider
                                            , executor: OperationExecutor
                                            , op: MonadicOp.AllocateResource
                                            ): F[Seq[NewObjectOp.NewResource[F]]]
}
