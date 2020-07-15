package izumi.distage.model.provisioning.strategies

import izumi.distage.model.effect.DIEffect
import izumi.distage.model.plan.ExecutableOp.ProxyOp
import izumi.distage.model.provisioning.{NewObjectOp, OperationExecutor, ProvisioningKeyProvider, WiringExecutor}
import izumi.reflect.TagK

trait ProxyStrategy {
  def makeProxy(context: ProvisioningKeyProvider, executor: WiringExecutor, makeProxy: ProxyOp.MakeProxy): Seq[NewObjectOp]
  def initProxy[F[_]: TagK: DIEffect](context: ProvisioningKeyProvider, executor: OperationExecutor, initProxy: ProxyOp.InitProxy): F[Seq[NewObjectOp]]
}
