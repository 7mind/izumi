package izumi.distage.model.provisioning.strategies

import izumi.distage.model.effect.DIEffect
import izumi.distage.model.plan.ExecutableOp.ProxyOp
import izumi.distage.model.provisioning.{NewObjectOp, OperationExecutor, ProvisioningKeyProvider}
import izumi.fundamentals.reflection.Tags.TagK

trait ProxyStrategy {
  def makeProxy(context: ProvisioningKeyProvider, makeProxy: ProxyOp.MakeProxy): Seq[NewObjectOp]
  def initProxy[F[_]: TagK: DIEffect](context: ProvisioningKeyProvider, executor: OperationExecutor, initProxy: ProxyOp.InitProxy): F[Seq[NewObjectOp]]
}

