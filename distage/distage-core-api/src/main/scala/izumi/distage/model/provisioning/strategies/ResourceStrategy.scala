package izumi.distage.model.provisioning.strategies

import izumi.distage.model.effect.DIEffect
import izumi.distage.model.plan.ExecutableOp.MonadicOp
import izumi.distage.model.provisioning.{NewObjectOp, OperationExecutor, ProvisioningKeyProvider}
import izumi.reflect.TagK

trait ResourceStrategy {
  def allocateResource[F[_]: TagK: DIEffect](context: ProvisioningKeyProvider, executor: OperationExecutor, op: MonadicOp.AllocateResource): F[Seq[NewObjectOp]]
}
