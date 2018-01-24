package org.bitbucket.pshirshov.izumi.distage.provisioning.strategies

import org.bitbucket.pshirshov.izumi.distage.commons.{ReflectionUtil, TraitTools}
import org.bitbucket.pshirshov.izumi.distage.model.plan.ExecutableOp
import org.bitbucket.pshirshov.izumi.distage.model.plan.ExecutableOp.WiringOp
import org.bitbucket.pshirshov.izumi.distage.provisioning.cglib.{CgLibFactoryMethodInterceptor, CglibTools}
import org.bitbucket.pshirshov.izumi.distage.provisioning.{OpResult, OperationExecutor, ProvisioningContext}

import scala.reflect.runtime._


class FactoryStrategyDefaultImpl extends FactoryStrategy {
  def makeFactory(context: ProvisioningContext, executor: OperationExecutor, f: WiringOp.InstantiateFactory): Seq[OpResult] = {
    // at this point we definitely have all the dependencies instantiated
    val allRequiredKeys = f.wiring.associations.map(_.wireWith).toSet
    val narrowed = mkExecutor(executor, context.narrow(allRequiredKeys))

    val wiredMethodIndex = makeIndex(f)

    val instanceType = f.wiring.factoryType
    val runtimeClass = currentMirror.runtimeClass(instanceType.tpe)
    val dispatcher = new CgLibFactoryMethodInterceptor(wiredMethodIndex, narrowed)

    CglibTools.mkdynamic(dispatcher, instanceType, runtimeClass, f) {
      instance =>
        TraitTools.initTrait(instanceType, runtimeClass, instance)
        Seq(OpResult.NewInstance(f.target, instance))
    }
  }

  private def makeIndex(f: WiringOp.InstantiateFactory) = {
    f.wiring.wirings.map {
      wiring =>
        ReflectionUtil.toJavaMethod(f.wiring.factoryType, wiring.factoryMethod) -> wiring
    }.toMap
  }

  private def mkExecutor(executor: OperationExecutor, newContext: ProvisioningContext) = {
    new OperationExecutor {
      override def execute(context: ProvisioningContext, step: ExecutableOp): Seq[OpResult] = {
        executor.execute(newContext, step)
      }
    }
  }
}

object FactoryStrategyDefaultImpl {
  final val instance = new FactoryStrategyDefaultImpl()
}

