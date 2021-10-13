package izumi.distage.provisioning

import izumi.distage.model.effect.QuasiIO
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
  override def execute[F[_]: TagK](context: ProvisioningKeyProvider, step: NonImportOp)(implicit F: QuasiIO[F]): F[Seq[NewObjectOp]] = {
    step match {
      case op: CreateSet =>
        F pure setStrategy.makeSet(context, op)

      case op: WiringOp =>
        op match {
          case op: WiringOp.UseInstance =>
            F pure instanceStrategy.getInstance(context, op)

          case op: WiringOp.ReferenceKey =>
            F pure instanceStrategy.getInstance(context, op)

          case op: WiringOp.CallProvider =>
            F pure providerStrategy.callProvider(context, op)
        }

      case op: ProxyOp.MakeProxy =>
        F pure proxyStrategy.makeProxy(context, op)

      case op: ProxyOp.InitProxy =>
        proxyStrategy.initProxy(context, this, op)

      case op: MonadicOp.ExecuteEffect =>
        F widen effectStrategy.executeEffect[F](context, this, op)

      case op: MonadicOp.AllocateResource =>
        F widen resourceStrategy.allocateResource[F](context, this, op)
    }
  }
}
