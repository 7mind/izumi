package izumi.distage.model.provisioning

import izumi.distage.model.effect.DIEffect
import izumi.distage.model.plan.ExecutableOp
import izumi.reflect.TagK

trait WiringExecutor {
  def execute(context: ProvisioningKeyProvider, step: ExecutableOp.WiringOp): Seq[NewObjectOp]
}

trait OperationExecutor extends WiringExecutor {
  def execute[F[_]: TagK: DIEffect](context: ProvisioningKeyProvider, step: ExecutableOp): F[Seq[NewObjectOp]]
}
