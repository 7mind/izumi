package com.github.pshirshov.izumi.distage.provisioning.cglib

import java.lang.reflect.Method

import com.github.pshirshov.izumi.distage.model.exceptions.DIException
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.WiringOp
import com.github.pshirshov.izumi.distage.model.provisioning.strategies.{JustExecutor, TraitIndex}
import com.github.pshirshov.izumi.distage.model.provisioning.{OpResult, OperationExecutor, ProvisioningContext}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeUniverse
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeUniverse.Wiring._
import com.github.pshirshov.izumi.distage.provisioning.FactoryTools
import com.github.pshirshov.izumi.fundamentals.reflection.TypeUtil
import net.sf.cglib.proxy.MethodProxy

protected[distage] class CgLibFactoryMethodInterceptor
(
  factoryMethodIndex: Map[Method, RuntimeUniverse.Wiring.FactoryMethod.WithContext]
  , dependencyMethodIndex: TraitIndex
  , narrowedContext: ProvisioningContext
  , executor: OperationExecutor
  , op: WiringOp.InstantiateFactory
) extends CgLibTraitMethodInterceptor(dependencyMethodIndex, narrowedContext) {

  override def intercept(o: scala.Any, method: Method, objects: Array[AnyRef], methodProxy: MethodProxy): AnyRef = {
    if (factoryMethodIndex.contains(method)) {
      val wiringWithContext = factoryMethodIndex(method)
      val justExecutor = mkExecutor(objects, wiringWithContext)

      val results = justExecutor.execute(
        FactoryTools.mkExecutableOp(op.target, wiringWithContext.wireWith)
      )

      FactoryTools.interpret(results)

    } else {
      super.intercept(o, method, objects, methodProxy)
    }
  }

  private def mkExecutor(arguments: Array[AnyRef], wiringWithContext: FactoryMethod.WithContext): JustExecutor = {
    if (arguments.length != wiringWithContext.methodArguments.length) {
      throw new DIException(s"Divergence between constructor arguments count: ${arguments.toSeq} vs ${wiringWithContext.methodArguments} ", null)
    }

    val providedValues = wiringWithContext.methodArguments.zip(arguments).toMap

    val unmatchedTypes = providedValues.filter {
      case (key, value) =>
        val runtimeClass = RuntimeUniverse.mirror.runtimeClass(key.symbol.tpe.erasure)
        !TypeUtil.isAssignableFrom(runtimeClass, value)
    }

    if (unmatchedTypes.nonEmpty) {
      throw new DIException(s"Divergence between constructor arguments types and provided values: $unmatchedTypes", null)
    }

    val extendedContext = narrowedContext.extend(providedValues)
    new JustExecutor {
      override def execute(step: ExecutableOp): Seq[OpResult] = {
        executor.execute(extendedContext, step)
      }
    }
  }

}


