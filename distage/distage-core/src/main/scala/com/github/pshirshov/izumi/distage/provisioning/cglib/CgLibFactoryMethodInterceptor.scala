package com.github.pshirshov.izumi.distage.provisioning.cglib

import java.lang.reflect.Method

import com.github.pshirshov.izumi.distage.model.exceptions.{DIException, UnsupportedWiringException}
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.WiringOp
import com.github.pshirshov.izumi.distage.model.plan.Wiring.{FactoryMethod, UnaryWiring}
import com.github.pshirshov.izumi.distage.model.plan.{ExecutableOp, Wiring}
import com.github.pshirshov.izumi.distage.model.provisioning.OpResult.{NewImport, NewInstance}
import com.github.pshirshov.izumi.distage.model.provisioning.strategies.{JustExecutor, TraitIndex}
import com.github.pshirshov.izumi.distage.model.provisioning.{OpResult, OperationExecutor, ProvisioningContext}
import com.github.pshirshov.izumi.distage.model.references.DIKey
import com.github.pshirshov.izumi.fundamentals.reflection.{RuntimeUniverse, TypeUtil}
import net.sf.cglib.proxy.MethodProxy

protected[distage] class CgLibFactoryMethodInterceptor
(
  factoryMethodIndex: Map[Method, Wiring.FactoryMethod.WithContext]
  , dependencyMethodIndex: TraitIndex
  , narrowedContext: ProvisioningContext
  , executor: OperationExecutor
  , f: WiringOp.InstantiateFactory
) extends CgLibTraitMethodInterceptor(dependencyMethodIndex, narrowedContext) {

  override def intercept(o: scala.Any, method: Method, objects: Array[AnyRef], methodProxy: MethodProxy): AnyRef = {
    if (factoryMethodIndex.contains(method)) {
      val wiringWithContext = factoryMethodIndex(method)
      val justExecutor = mkExecutor(objects, wiringWithContext)

      val results = wiringWithContext.wireWith match {
        case w: UnaryWiring.Constructor =>
          val target = DIKey.ProxyElementKey(f.target, w.instanceType)
          justExecutor.execute(ExecutableOp.WiringOp.InstantiateClass(target, w))

        case w: UnaryWiring.Abstract =>
          val target = DIKey.ProxyElementKey(f.target, w.instanceType)
          justExecutor.execute(ExecutableOp.WiringOp.InstantiateTrait(target, w))

        case w =>
          throw new UnsupportedWiringException(s"Wiring unsupported: $w", f.wiring.factoryType)
      }

      interpret(results)

    } else {
      super.intercept(o, method, objects, methodProxy)
    }
  }

  private def mkExecutor(objects: Array[AnyRef], wiringWithContext: FactoryMethod.WithContext) = {
    if (objects.length != wiringWithContext.signature.length) {
      throw new DIException(s"Divergence between constructor arguments count: ${objects.toSeq} vs ${wiringWithContext.signature} ", null)
    }

    val providedValues = wiringWithContext.signature.zip(objects).toMap

    val unmatchedTypes = providedValues.filter {
      case (key, value) =>
        val runtimeClass = RuntimeUniverse.mirror.runtimeClass(key.symbol.tpe)
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

  private def interpret(results: Seq[OpResult]) = {
    results.headOption match {
      case Some(i: NewInstance) =>
        i.value.asInstanceOf[AnyRef]
      case Some(i: NewImport) =>
        i.value.asInstanceOf[AnyRef]
      case _ =>
        throw new DIException(s"Factory cannot interpret $results", null)
    }
  }
}


