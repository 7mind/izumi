package org.bitbucket.pshirshov.izumi.distage.provisioning

import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicReference

import net.sf.cglib.proxy.{MethodInterceptor, MethodProxy}
import org.bitbucket.pshirshov.izumi.distage.model.DIKey
import org.bitbucket.pshirshov.izumi.distage.model.exceptions.MissingRefException

protected[distage] class CglibRefDispatcher(key: DIKey) extends MethodInterceptor {
  val reference = new AtomicReference[AnyRef](null)


  override def intercept(o: scala.Any, method: Method, objects: Array[AnyRef], methodProxy: MethodProxy): AnyRef = {
    Option(reference.get()) match {
      case Some(v) =>
        methodProxy.invokeSuper(v, objects)
      case _ if method.getName == "toString" && method.getParameterCount == 0 =>
        s"UninitializedProxy:$key"
      case _=>
        throw new MissingRefException(s"Proxy $key is not yet initialized", Set(key), None)
    }
  }

}


