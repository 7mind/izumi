package izumi.distage.model.provisioning

import izumi.distage.model.monadic.DIEffect
import izumi.distage.model.plan.ExecutableOp
import izumi.fundamentals.reflection.Tags.TagK

trait OperationExecutor {
  def execute[F[_]: TagK: DIEffect](context: ProvisioningKeyProvider, step: ExecutableOp): F[Seq[NewObjectOp]]
}

trait WiringExecutor {
  def execute(context: ProvisioningKeyProvider, step: ExecutableOp.WiringOp): Seq[NewObjectOp]
}
