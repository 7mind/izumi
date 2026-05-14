package izumi.distage.model.provisioning.strategies

import izumi.distage.model.definition.errors.ProvisionerIssue
import izumi.functional.bio.IO2
import izumi.distage.model.plan.ExecutableOp.ProxyOp
import izumi.distage.model.provisioning.{NewObjectOp, OperationExecutor, ProvisioningKeyProvider}
import izumi.reflect.TagK

trait ProxyStrategy {
  def makeProxy[F[+_, +_]: TagK: IO2](context: ProvisioningKeyProvider, makeProxy: ProxyOp.MakeProxy): F[Throwable, Either[ProvisionerIssue, Seq[NewObjectOp]]]
  def initProxy[F[+_, +_]: TagK: IO2](
    context: ProvisioningKeyProvider,
    executor: OperationExecutor,
    initProxy: ProxyOp.InitProxy,
  ): F[Throwable, Either[ProvisionerIssue, Seq[NewObjectOp]]]
}
