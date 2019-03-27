package com.github.pshirshov.izumi.distage.provisioning.strategies

import com.github.pshirshov.izumi.distage.model.definition.DIResource.DIResourceBase
import com.github.pshirshov.izumi.distage.model.exceptions.UnexpectedProvisionResultException
import com.github.pshirshov.izumi.distage.model.monadic.DIEffect
import com.github.pshirshov.izumi.distage.model.monadic.DIEffect.syntax._
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.MonadicOp
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.MonadicOp.AllocateResource
import com.github.pshirshov.izumi.distage.model.provisioning.strategies.ResourceStrategy
import com.github.pshirshov.izumi.distage.model.provisioning.{NewObjectOp, OperationExecutor, ProvisioningKeyProvider}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.{SafeType, TagK}
import com.github.pshirshov.izumi.fundamentals.platform.functional.Identity

class ResourceStrategyDefaultImpl
  extends ResourceStrategy {

  protected[this] val identityEffectType: RuntimeDIUniverse.SafeType = SafeType.getK[Identity]

  override def allocateResource[F[_]: TagK](
                                             context: ProvisioningKeyProvider
                                           , executor: OperationExecutor
                                           , op: MonadicOp.AllocateResource
                                           )(implicit F: DIEffect[F]): F[Seq[NewObjectOp.NewResource[F]]] = {
    val provisionerEffectType = SafeType.getK[F]
    val AllocateResource(target, actionOp, _, _) = op
    val actionEffectType = op.wiring.effectHKTypeCtor

    val isEffect = actionEffectType != identityEffectType
    if (isEffect && !(actionEffectType <:< provisionerEffectType)) {
      // FIXME: should be thrown earlier [imports (or missing non-imports???) too; add more sanity checks wrt imports after GC, etc.]
      throw new ThisException_ShouldBePartOfPrepSanityCheckReally_SameAsImports(
        s"Incompatible effect types $actionEffectType <!:< $provisionerEffectType"
      )
    }

    executor.execute(context, actionOp)
      .flatMap(_.toList match {
        case NewObjectOp.NewInstance(_, resource0) :: Nil if isEffect =>
          val resource = resource0.asInstanceOf[DIResourceBase[F, Any]]
          resource.acquire.map {
            innerResource =>
              Seq(NewObjectOp.NewResource[F](target, resource.extract(innerResource), () => resource.release(innerResource)))
          }
        case NewObjectOp.NewInstance(_, resourceSimple) :: Nil =>
          val resource = resourceSimple.asInstanceOf[DIResourceBase[Identity, Any]]
          F.maybeSuspend(resource.acquire).map {
            innerResource =>
              Seq(NewObjectOp.NewResource[F](target, resource.extract(innerResource), () => F.maybeSuspend(resource.release(innerResource))))
          }
        case r =>
          throw new UnexpectedProvisionResultException(s"Unexpected operation result for ${actionOp.target}: $r, expected a single NewInstance!", r)
      })
  }

}
