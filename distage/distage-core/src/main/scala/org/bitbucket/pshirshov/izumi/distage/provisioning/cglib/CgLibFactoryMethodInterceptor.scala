package org.bitbucket.pshirshov.izumi.distage.provisioning.cglib

import java.lang.reflect.Method

import net.sf.cglib.proxy.MethodProxy
import org.bitbucket.pshirshov.izumi.distage.model.DIKey
import org.bitbucket.pshirshov.izumi.distage.model.exceptions.{DIException, UnsupportedWiringException}
import org.bitbucket.pshirshov.izumi.distage.model.plan.ExecutableOp.WiringOp
import org.bitbucket.pshirshov.izumi.distage.model.plan.{Association, ExecutableOp, UnaryWiring, Wiring}
import org.bitbucket.pshirshov.izumi.distage.provisioning.OpResult.{NewImport, NewInstance}
import org.bitbucket.pshirshov.izumi.distage.provisioning.strategies.JustExecutor
import org.bitbucket.pshirshov.izumi.distage.provisioning.{OpResult, OperationExecutor, ProvisioningContext}

protected[distage] class CgLibFactoryMethodInterceptor
(
  factoryMethodIndex: Map[Method, Wiring.FactoryMethod.WithContext]
  , dependencyMethodIndex: Map[Method, Association.Method]
  , narrowedContext: ProvisioningContext
  , executor: OperationExecutor
  , f: WiringOp.InstantiateFactory
) extends CgLibTraitMethodInterceptor(dependencyMethodIndex, narrowedContext) {

  override def intercept(o: scala.Any, method: Method, objects: Array[AnyRef], methodProxy: MethodProxy): AnyRef = {
    if (factoryMethodIndex.contains(method)) {
      val wiringWithContext = factoryMethodIndex(method)
      wiringWithContext.wireWith match {
        case w: UnaryWiring.Constructor =>
          val target = DIKey.ProxyElementKey(f.target, w.instanceType)

          if (objects.length != wiringWithContext.signature.length) {
            throw new DIException(s"Divergence between constructor arguments count: ${objects.toSeq} vs ${wiringWithContext.signature} ", null)
          }

          val providedValues = wiringWithContext.signature.zip(objects).toMap
          val extendedContext = narrowedContext.extend(providedValues)
          val justExecutor = mkExecutor(executor, extendedContext)
          val results = justExecutor.execute(ExecutableOp.WiringOp.InstantiateClass(target, w))
          interpret(results)

        case w: UnaryWiring.Abstract =>
          val target = DIKey.ProxyElementKey(f.target, w.instanceType)
          val justExecutor = mkExecutor(executor, narrowedContext)
          val results = justExecutor.execute(ExecutableOp.WiringOp.InstantiateTrait(target, w))
          interpret(results)

        case w =>
          throw new UnsupportedWiringException(s"Wiring unsupported: $w", f.wiring.factoryType)
      }
    } else {
      super.intercept(o, method, objects, methodProxy)
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

  private def mkExecutor(executor: OperationExecutor, newContext: ProvisioningContext) = {
    new JustExecutor {
      override def execute(step: ExecutableOp): Seq[OpResult] = {
        executor.execute(newContext, step)
      }
    }
  }
}


