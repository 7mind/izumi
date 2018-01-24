package org.bitbucket.pshirshov.izumi.distage.provisioning.cglib

import java.lang.reflect.Method

import net.sf.cglib.proxy.{MethodInterceptor, MethodProxy}
import org.bitbucket.pshirshov.izumi.distage.model.DIKey
import org.bitbucket.pshirshov.izumi.distage.model.exceptions.{DIException, MissingRefException, UnsupportedWiringException}
import org.bitbucket.pshirshov.izumi.distage.model.plan.ExecutableOp.WiringOp
import org.bitbucket.pshirshov.izumi.distage.model.plan.{Association, ExecutableOp, UnaryWiring, Wiring}
import org.bitbucket.pshirshov.izumi.distage.provisioning.{OpResult, ProvisioningContext}
import org.bitbucket.pshirshov.izumi.distage.provisioning.OpResult.{NewImport, NewInstance}
import org.bitbucket.pshirshov.izumi.distage.provisioning.strategies.JustExecutor

protected[distage] class CgLibFactoryMethodInterceptor
(
  factoryMethodIndex: Map[Method, Wiring.FactoryMethod.WithContext]
  , dependencyMethodIndex: Map[Method, Association.Method]
  , context: JustExecutor
  , provisioningContext: ProvisioningContext
  , f: WiringOp.InstantiateFactory
) extends MethodInterceptor {

  override def intercept(o: scala.Any, method: Method, objects: Array[AnyRef], methodProxy: MethodProxy): AnyRef = {
    //premature optimization, all our methods are parameterless
    if (factoryMethodIndex.contains(method)) {
      factoryMethodIndex(method).wireWith match {
        case w: UnaryWiring.Constructor =>
          val target = DIKey.ProxyElementKey(f.target, w.instanceType)
          val results = context.execute(ExecutableOp.WiringOp.InstantiateClass(target, w))
          interpret(results)

        case w: UnaryWiring.Abstract =>
          val target = DIKey.ProxyElementKey(f.target, w.instanceType)
          val results = context.execute(ExecutableOp.WiringOp.InstantiateTrait(target, w))
          interpret(results)

        case w =>
          throw new UnsupportedWiringException(s"Wiring unsupported: $w", f.wiring.factoryType)
      }
    } else if(dependencyMethodIndex.contains(method)) {
      // TODO: copypasted from trait interceptor
      val wireWith = dependencyMethodIndex(method).wireWith
      provisioningContext.fetchKey(wireWith) match {
        case Some(v) =>
          v.asInstanceOf[AnyRef]

        case None =>
          throw new MissingRefException(s"Cannot return $wireWith from ${method.getName}, it's not available in the context o_O", Set(wireWith), None)
      }
    } else {
      CglibTools.invokeExistingMethod(o, method, objects)
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


