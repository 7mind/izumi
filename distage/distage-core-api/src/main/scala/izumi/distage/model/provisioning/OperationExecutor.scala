package izumi.distage.model.provisioning

import izumi.distage.model.effect.QuasiIO
import izumi.distage.model.plan.ExecutableOp
import izumi.distage.model.plan.ExecutableOp.NonImportOp
import izumi.reflect.TagK

trait WiringExecutor {
  def execute(context: ProvisioningKeyProvider, step: ExecutableOp.WiringOp): Seq[NewObjectOp]
}

trait OperationExecutor extends WiringExecutor {
  def execute[F[_]: TagK: QuasiIO](context: ProvisioningKeyProvider, step: NonImportOp): F[Seq[NewObjectOp]]
}
