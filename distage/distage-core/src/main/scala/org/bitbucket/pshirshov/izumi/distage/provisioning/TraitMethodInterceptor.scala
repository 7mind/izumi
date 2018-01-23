package org.bitbucket.pshirshov.izumi.distage.provisioning

import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicReference

import net.sf.cglib.proxy.{MethodInterceptor, MethodProxy}
import org.bitbucket.pshirshov.izumi.distage.commons.Value
import org.bitbucket.pshirshov.izumi.distage.model.DIKey
import org.bitbucket.pshirshov.izumi.distage.model.exceptions.MissingRefException
import org.bitbucket.pshirshov.izumi.distage.model.plan.Association

protected[distage] class TraitMethodInterceptor(context: ProvisioningContext, key: DIKey) extends MethodInterceptor {
  val methods = new AtomicReference[Map[String, Association.Method]]()

  override def intercept(o: scala.Any, method: Method, objects: Array[AnyRef], methodProxy: MethodProxy): AnyRef = {
    val safeMethods = Value(methods)
      .map(_.get())
      .eff(checkNotNull)
      .get

    val methodName = method.getName

    if (method.getParameterTypes.length == 0 && safeMethods.contains(methodName)) {
      val wireWith = safeMethods(methodName).wireWith

      context.fetchKey(wireWith) match {
        case Some(v) =>
          v.asInstanceOf[AnyRef]
        case None =>
          throw new MissingRefException(s"Cannot return $wireWith from $methodName, it's not available in the context o_O", Set(wireWith), None)
      }
      
    } else {
      methodProxy.invokeSuper(o, objects)
    }
  }

  protected def checkNotNull(ref: AnyRef): Unit = {
    if (ref == null) {
      throw new MissingRefException(s"Trait initialization has not been finished: $key", Set(key), None)
    }
  }
}
