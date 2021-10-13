package izumi.distage.provisioning

import izumi.distage.model.effect.QuasiIO
import izumi.distage.model.exceptions.{ProvisionerIssue, UnexpectedDIException}
import izumi.distage.model.plan.ExecutableOp.{CreateSet, MonadicOp, NonImportOp, ProxyOp, WiringOp}
import izumi.distage.model.provisioning.strategies.*
import izumi.distage.model.provisioning.{NewObjectOp, OperationExecutor, ProvisioningKeyProvider}
import izumi.reflect.TagK
import izumi.distage.model.effect.QuasiIO.syntax.*

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
    for {
      maybeResult <- F.definitelyRecover[Either[ProvisionerIssue, Seq[NewObjectOp]]](
        action = executeUnsafe(context, step)
      )(recover = exception => F.pure(Left(UnexpectedDIException(step, exception))))
    } yield {
      maybeResult
    }
  }

  private[this] def executeUnsafe[F[_]: TagK](
    context: ProvisioningKeyProvider,
    step: NonImportOp,
  )(implicit F: QuasiIO[F]
  ): F[Either[ProvisionerIssue, Seq[NewObjectOp]]] = {
    step match {
      case op: CreateSet =>
        F.pure(Right(setStrategy.makeSet(context, op)))

      case op: WiringOp =>
        op match {
          case op: WiringOp.UseInstance =>
            F.pure(Right(instanceStrategy.getInstance(context, op)))

          case op: WiringOp.ReferenceKey =>
            F.pure(Right(instanceStrategy.getInstance(context, op)))

          case op: WiringOp.CallProvider =>
            F.pure(Right(providerStrategy.callProvider(context, op)))
        }

      case op: ProxyOp.MakeProxy =>
        F.pure(Right(proxyStrategy.makeProxy(context, op)))

      case op: ProxyOp.InitProxy =>
        proxyStrategy.initProxy(context, this, op)

      case op: MonadicOp.ExecuteEffect =>
        F.map(effectStrategy.executeEffect[F](context, op))(Right.apply)

      case op: MonadicOp.AllocateResource =>
        F.map(resourceStrategy.allocateResource[F](context, op))(Right.apply)
    }
  }

}
