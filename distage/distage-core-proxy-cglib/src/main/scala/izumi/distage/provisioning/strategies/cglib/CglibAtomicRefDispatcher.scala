package izumi.distage.provisioning.strategies.cglib

import java.lang.reflect.Method

import izumi.distage.model.provisioning.proxies.ProxyDispatcher.AtomicProxyDispatcher
import net.sf.cglib.proxy.{MethodInterceptor, MethodProxy}

// dynamic dispatching is not optimal, uhu
private[distage] class CglibAtomicRefDispatcher(
  nullProxy: AnyRef
) extends AtomicProxyDispatcher
  with MethodInterceptor {

  override def intercept(o: scala.Any, method: Method, objects: Array[AnyRef], methodProxy: MethodProxy): AnyRef = {
    val methodName = method.getName
    if (methodName == "equals" && (method.getParameterTypes sameElements Array(classOf[AnyRef]))) {
      objects.headOption match {
        case Some(r: DistageProxy) =>
          Boolean.box(getRef() == r._distageProxyReference)

        case _ =>
          method.invoke(getRef(), objects: _*)
      }
    } else if (methodName == "_distageProxyReference" && method.getParameterCount == 0) {
      getRef()
    } else {
      method.invoke(getRef(), objects: _*)
    }
  }

  @inline private[this] final def getRef(): AnyRef = {
    val value = reference.get()
    if (value ne null) value else nullProxy
  }
}
