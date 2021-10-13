package izumi.distage.provisioning.strategies

import izumi.distage.model.exceptions.MissingImport
import izumi.distage.model.plan.ExecutableOp.ImportDependency
import izumi.distage.model.provisioning.strategies.ImportStrategy
import izumi.distage.model.provisioning.{NewObjectOp, ProvisioningKeyProvider}

class ImportStrategyDefaultImpl extends ImportStrategy {
  override def importDependency(context: ProvisioningKeyProvider, op: ImportDependency): Either[MissingImport, Seq[NewObjectOp]] = {
    context.importKey(op.target) match {
      case Some(v) =>
        Right(Seq(NewObjectOp.NewImport(op.target, v)))
      case _ =>
        Left(MissingImport(op))
    }
  }
}
