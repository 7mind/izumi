package org.bitbucket.pshirshov.izumi.distage.provisioning.cglib

import java.lang.reflect.Method

import net.sf.cglib.proxy.{MethodInterceptor, MethodProxy}
import org.bitbucket.pshirshov.izumi.distage.model.plan.Wiring
import org.bitbucket.pshirshov.izumi.distage.provisioning.OperationExecutor

protected[distage] class CgLibFactoryMethodInterceptor
(
  index: Map[Method, Wiring.FactoryMethod.WithContext]
  , context: OperationExecutor
) extends MethodInterceptor {

  override def intercept(o: scala.Any, method: Method, objects: Array[AnyRef], methodProxy: MethodProxy): AnyRef = {
    //premature optimization, all our methods are parameterless
    if (method.getParameterTypes.length == 0 && index.contains(method)) {
      val wireWith = index(method).wireWith

      ???
//      context.fetchKey(wireWith) match {
//        case Some(v) =>
//          ??? // call provisioner here
//
//        case None =>
//          throw new MissingRefException(s"Cannot return $wireWith from ${method.getName}, it's not available in the context o_O", Set(wireWith), None)
//      }

    } else {
      CglibTools.invokeExistingMethod(o, method, objects)
    }
  }

}


