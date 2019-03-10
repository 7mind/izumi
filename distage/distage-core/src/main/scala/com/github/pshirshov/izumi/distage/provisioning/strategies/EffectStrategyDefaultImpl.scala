package com.github.pshirshov.izumi.distage.provisioning.strategies

import com.github.pshirshov.izumi.distage.model.exceptions.{DIException, UnexpectedProvisionResultException}
import com.github.pshirshov.izumi.distage.model.monadic.DIMonad
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.WiringOp.MonadicOp
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.WiringOp.MonadicOp.ExecuteEffect
import com.github.pshirshov.izumi.distage.model.provisioning.{NewObjectOp, OperationExecutor, ProvisioningKeyProvider}
import com.github.pshirshov.izumi.distage.model.provisioning.strategies.EffectStrategy
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.TagK
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.SafeType

class EffectStrategyDefaultImpl
  extends EffectStrategy {

  override def executeEffect[F[_] : TagK : DIMonad](
                                                     context: ProvisioningKeyProvider,
                                                     executor: OperationExecutor,
                                                     op: MonadicOp.ExecuteEffect
                                                   ): F[Seq[NewObjectOp.NewInstance]] = {
    val provisionerEffectType = SafeType.getK[F]
    val ExecuteEffect(target, actionOp, _, _) = op
    val actionEffectType = op.wiring.effectHKTypeCtor

    if (!(actionEffectType <:< provisionerEffectType)) {
      // FIXME: should be thrown earlier [imports (or missing non-imports???) too; add more sanity checks wrt imports after GC, etc.]
      throw new ThisException_ShouldBePartOfPrepSanityCheckReally_SameAsImports(
        s"Incompatible effect types $actionEffectType <!:< $provisionerEffectType"
      )
    }

    executor.execute(context, actionOp).toList match {
      case NewObjectOp.NewInstance(_, action0) :: Nil =>
        val action = action0.asInstanceOf[F[Any]]
        DIMonad[F].map(action) {
          instance =>
            Seq(NewObjectOp.NewInstance(target, instance))
        }
      case r =>
        throw new UnexpectedProvisionResultException(s"Unexpected operation result for ${actionOp.target}: $r, expected a single NewInstance!", r)
    }
  }

}
class ThisException_ShouldBePartOfPrepSanityCheckReally_SameAsImports(m: String) extends DIException(m, null)
