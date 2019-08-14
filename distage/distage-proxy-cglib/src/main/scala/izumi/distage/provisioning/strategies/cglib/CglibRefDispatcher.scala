package izumi.distage.provisioning.strategies.cglib

import java.lang.reflect.Method

import izumi.distage.model.provisioning.strategies.{AtomicProxyDispatcher, DistageProxy}
import izumi.distage.model.reflection.universe.RuntimeDIUniverse
import net.sf.cglib.proxy.{MethodInterceptor, MethodProxy}

// dynamic dispatching is not optimal, uhu
protected[distage] class CglibRefDispatcher(val key: RuntimeDIUniverse.DIKey, nullProxy: AnyRef)
  extends AtomicProxyDispatcher
    with MethodInterceptor {


  override def intercept(o: scala.Any, method: Method, objects: Array[AnyRef], methodProxy: MethodProxy): AnyRef = {
    val methodName = method.getName
    if (methodName == "equals" && (method.getParameterTypes sameElements Array(classOf[AnyRef]))) {
      objects.headOption match {
        case Some(r: DistageProxy) =>
          Boolean.box(getRef == r._distageProxyReference)

        case _ =>
          method.invoke(getRef, objects: _*)
      }
    } else if (methodName == "_distageProxyReference" && method.getParameterCount == 0) {
      getRef
    } else {
      method.invoke(getRef, objects: _*)
    }
  }

  @inline def getRef: AnyRef = {
    val value: AnyRef = reference.get()
    if (value != null) {
      value
    } else {
      nullProxy
    }
  }
}
