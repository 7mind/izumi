package izumi.distage.provisioning

import izumi.distage.model.effect.QuasiIO
import izumi.distage.model.exceptions.interpretation.ProvisionerIssue
import izumi.distage.model.exceptions.interpretation.ProvisionerIssue.ProvisionerExceptionIssue.UnexpectedStepProvisioningException
import izumi.distage.model.plan.ExecutableOp.{CreateSet, MonadicOp, NonImportOp, ProxyOp, WiringOp}
import izumi.distage.model.provisioning.strategies.*
import izumi.distage.model.provisioning.{NewObjectOp, OperationExecutor, ProvisioningKeyProvider}
import izumi.reflect.TagK

class OperationExecutorImpl(
  setStrategy: SetStrategy,
  proxyStrategy: ProxyStrategy,
  providerStrategy: ProviderStrategy,
  instanceStrategy: InstanceStrategy,
  effectStrategy: EffectStrategy,
  resourceStrategy: ResourceStrategy,
) extends OperationExecutor {

  override def execute[F[_]: TagK](
    context: ProvisioningKeyProvider,
    step: NonImportOp,
  )(implicit F: QuasiIO[F]
  ): F[Either[ProvisionerIssue, Seq[NewObjectOp]]] = {
    F.definitelyRecover(
      executeUnsafe(context, step)
    )(err => F.pure(Left(UnexpectedStepProvisioningException(step, err))))
  }

  private[this] def executeUnsafe[F[_]: TagK](
    context: ProvisioningKeyProvider,
    step: NonImportOp,
  )(implicit F: QuasiIO[F]
  ): F[Either[ProvisionerIssue, Seq[NewObjectOp]]] = step match {
    case op: CreateSet =>
      F.maybeSuspend(Right(setStrategy.makeSet(context, op)))

    case op: WiringOp.UseInstance =>
      F.maybeSuspend(Right(instanceStrategy.getInstance(context, op)))

    case op: WiringOp.ReferenceKey =>
      F.maybeSuspend(Right(instanceStrategy.getInstance(context, op)))

    case op: WiringOp.CallProvider =>
      providerStrategy.callProvider(context, op)

    case op: ProxyOp.MakeProxy =>
      proxyStrategy.makeProxy(context, op)

    case op: ProxyOp.InitProxy =>
      proxyStrategy.initProxy(context, this, op)

    case op: MonadicOp.ExecuteEffect =>
      F.map(effectStrategy.executeEffect[F](context, op))(Right(_))

    case op: MonadicOp.AllocateResource =>
      F.map(resourceStrategy.allocateResource[F](context, op))(Right(_))
  }

}
