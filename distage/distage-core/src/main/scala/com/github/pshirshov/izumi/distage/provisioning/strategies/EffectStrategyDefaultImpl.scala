package com.github.pshirshov.izumi.distage.provisioning.strategies

import com.github.pshirshov.izumi.distage.model.exceptions.{DIException, UnexpectedProvisionResultException}
import com.github.pshirshov.izumi.distage.model.monadic.DIEffect
import com.github.pshirshov.izumi.distage.model.monadic.DIEffect.syntax._
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.MonadicOp
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.MonadicOp.ExecuteEffect
import com.github.pshirshov.izumi.distage.model.provisioning.{NewObjectOp, OperationExecutor, ProvisioningKeyProvider}
import com.github.pshirshov.izumi.distage.model.provisioning.strategies.EffectStrategy
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.TagK
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.SafeType

class EffectStrategyDefaultImpl
  extends EffectStrategy {

  override def executeEffect[F[_]: TagK](
                                          context: ProvisioningKeyProvider,
                                          executor: OperationExecutor,
                                          op: MonadicOp.ExecuteEffect
                                        )(implicit F: DIEffect[F]): F[Seq[NewObjectOp.NewInstance]] = {
    val provisionerEffectType = SafeType.getK[F]
    val ExecuteEffect(target, actionOp, _, _) = op
    val actionEffectType = op.wiring.effectHKTypeCtor

    if (!(actionEffectType <:< provisionerEffectType)) {
      // FIXME: should be thrown earlier [imports (or missing non-imports???) too; add more sanity checks wrt imports after GC, etc.]
      throw new ThisException_ShouldBePartOfPrepSanityCheckReally_SameAsImports(
        s"Incompatible effect types $actionEffectType <!:< $provisionerEffectType"
      )
    }

    executor.execute(context, actionOp).flatMap(_.toList match {
      case NewObjectOp.NewInstance(_, action0) :: Nil =>
        val action = action0.asInstanceOf[F[Any]]
        F.map(action)(newInstance => Seq(NewObjectOp.NewInstance(target, newInstance)))
      case r =>
        throw new UnexpectedProvisionResultException(s"Unexpected operation result for ${actionOp.target}: $r, expected a single NewInstance!", r)
    })
  }

}
class ThisException_ShouldBePartOfPrepSanityCheckReally_SameAsImports(m: String) extends DIException(m, null)
