package org.bitbucket.pshirshov.izumi.distage.provisioning

import java.lang.reflect.Method

import net.sf.cglib.proxy.{MethodInterceptor, MethodProxy}
import org.bitbucket.pshirshov.izumi.distage.model.DIKey
import org.bitbucket.pshirshov.izumi.distage.model.exceptions.MissingRefException
import org.bitbucket.pshirshov.izumi.distage.model.plan.Association

protected[distage] class TraitMethodInterceptor(index: Map[Method, Association.Method], context: ProvisioningContext, key: DIKey) extends MethodInterceptor {
  override def intercept(o: scala.Any, method: Method, objects: Array[AnyRef], methodProxy: MethodProxy): AnyRef = {
    //premature optimization, all our methods are parameterless
    if (method.getParameterTypes.length == 0 && index.contains(method)) {
      val wireWith = index(method).wireWith

      context.fetchKey(wireWith) match {
        case Some(v) =>
          v.asInstanceOf[AnyRef]

        case None =>
          throw new MissingRefException(s"Cannot return $wireWith from ${method.getName}, it's not available in the context o_O", Set(wireWith), None)
      }
      
    } else {
      // TODO: we can't invoke super method here, trait is just an interface from java prospective
      methodProxy.invokeSuper(o, objects)
    }
  }
}
