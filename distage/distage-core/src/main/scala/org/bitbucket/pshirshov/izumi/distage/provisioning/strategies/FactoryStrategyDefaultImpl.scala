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
    val narrowedContext = context.narrow(allRequiredKeys)
    val justExecutor = mkExecutor(executor, narrowedContext)

    val factoryMethodIndex = makeFactoryIndex(f)
    val depMethodIndex = TraitStrategyDefaultImpl.makeIndex(f.wiring.dependencies)

    val instanceType = f.wiring.factoryType
    val runtimeClass = currentMirror.runtimeClass(instanceType.tpe)
    val dispatcher = new CgLibFactoryMethodInterceptor(factoryMethodIndex, depMethodIndex, justExecutor, narrowedContext, f)

    CglibTools.mkdynamic(dispatcher, instanceType, runtimeClass, f) {
      instance =>
        TraitTools.initTrait(instanceType, runtimeClass, instance)
        Seq(OpResult.NewInstance(f.target, instance))
    }
  }

  private def makeFactoryIndex(f: WiringOp.InstantiateFactory) = {
    f.wiring.wirings.map {
      wiring =>
        ReflectionUtil.toJavaMethod(f.wiring.factoryType, wiring.factoryMethod) -> wiring
    }.toMap
  }

//  private def makeDependencyIndex(t: WiringOp.InstantiateTrait): Map[Method, Association.Method] = {
//    t.wiring.associations.map {
//      m =>
//        ReflectionUtil.toJavaMethod(m.context.definingClass, m.symbol) -> m
//    }.toMap
//  }

  private def mkExecutor(executor: OperationExecutor, newContext: ProvisioningContext) = {
    new JustExecutor {
      override def execute(step: ExecutableOp): Seq[OpResult] = {
        executor.execute(newContext, step)
      }
    }
  }
}

trait JustExecutor {
  def execute(step: ExecutableOp): Seq[OpResult]
}

object FactoryStrategyDefaultImpl {
  final val instance = new FactoryStrategyDefaultImpl()
}

