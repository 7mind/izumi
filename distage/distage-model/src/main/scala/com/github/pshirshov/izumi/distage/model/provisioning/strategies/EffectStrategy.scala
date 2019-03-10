package com.github.pshirshov.izumi.distage.model.provisioning.strategies

import com.github.pshirshov.izumi.distage.model.monadic.DIMonad
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.WiringOp.MonadicOp
import com.github.pshirshov.izumi.distage.model.provisioning.{NewObjectOp, OperationExecutor, ProvisioningKeyProvider}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.TagK

trait EffectStrategy {
  def executeEffect[F[_]: TagK: DIMonad](
                                          context: ProvisioningKeyProvider
                                        , executor: OperationExecutor
                                        , op: MonadicOp.ExecuteEffect
                                        ): F[Seq[NewObjectOp.NewInstance]]
}
