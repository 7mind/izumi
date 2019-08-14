package izumi.distage.model.provisioning.strategies

import izumi.distage.model.monadic.DIEffect
import izumi.distage.model.plan.ExecutableOp.ProxyOp
import izumi.distage.model.provisioning.{NewObjectOp, OperationExecutor, ProvisioningKeyProvider}
import izumi.distage.model.reflection.universe.RuntimeDIUniverse.TagK

trait ProxyStrategy {
  def initProxy[F[_]: TagK: DIEffect](context: ProvisioningKeyProvider, executor: OperationExecutor, initProxy: ProxyOp.InitProxy): F[Seq[NewObjectOp]]

  def makeProxy(context: ProvisioningKeyProvider, makeProxy: ProxyOp.MakeProxy): Seq[NewObjectOp]
}

