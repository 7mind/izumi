package izumi.distage.provisioning

import izumi.distage.model.definition.errors.ProvisionerIssue
import izumi.functional.bio.{Exit, IO2}
import ProvisionerIssue.ProvisionerExceptionIssue.UnexpectedStepProvisioning
import izumi.distage.model.plan.ExecutableOp.{CreateSet, MonadicOp, NonImportOp, ProxyOp, WiringOp}
import izumi.distage.model.provisioning.strategies.*
import izumi.distage.model.provisioning.{NewObjectOp, OperationExecutor, ProvisioningKeyProvider}
import izumi.reflect.TagKK

class OperationExecutorImpl(
  setStrategy: SetStrategy,
  proxyStrategy: ProxyStrategy,
  providerStrategy: ProviderStrategy,
  instanceStrategy: InstanceStrategy,
  effectStrategy: EffectStrategy,
  resourceStrategy: ResourceStrategy,
  subcontextStrategy: SubcontextStrategy,
) extends OperationExecutor {

  override def execute[F[+_, +_]: TagKK](
    context: ProvisioningKeyProvider,
    step: NonImportOp,
  )(implicit F: IO2[F]
  ): F[Throwable, Either[ProvisionerIssue, Seq[NewObjectOp]]] = {
    F.sandboxCatchAll[Throwable, Either[ProvisionerIssue, Seq[NewObjectOp]], Throwable](
      F.suspendThrowable(executeUnsafe(context, step))
    )(
      (failure: Exit.FailureUninterrupted[Throwable]) =>
        F.pure(Left(UnexpectedStepProvisioning(step, failure.trace.unsafeAttachTraceOrReturnNewThrowable())))
    )
  }

  private def executeUnsafe[F[+_, +_]: TagKK](
    context: ProvisioningKeyProvider,
    step: NonImportOp,
  )(implicit F: IO2[F]
  ): F[Throwable, Either[ProvisionerIssue, Seq[NewObjectOp]]] = step match {
    case op: CreateSet =>
      setStrategy.makeSet(context, op)

    case op: WiringOp.UseInstance =>
      instanceStrategy.getInstance(context, op)

    case op: WiringOp.ReferenceKey =>
      instanceStrategy.getInstance(context, op)

    case op: WiringOp.CallProvider =>
      providerStrategy.callProvider(context, op)

    case op: WiringOp.CreateSubcontext =>
      subcontextStrategy.prepareSubcontext(context, op)

    case op: ProxyOp.MakeProxy =>
      proxyStrategy.makeProxy(context, op)

    case op: ProxyOp.InitProxy =>
      proxyStrategy.initProxy(context, this, op)

    case op: MonadicOp.ExecuteEffect =>
      effectStrategy.executeEffect[F](context, op)

    case op: MonadicOp.AllocateResource =>
      resourceStrategy.allocateResource[F](context, op)
  }

}
