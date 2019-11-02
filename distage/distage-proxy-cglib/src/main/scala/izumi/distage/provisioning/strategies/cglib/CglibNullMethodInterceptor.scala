package izumi.distage.provisioning.strategies.cglib

import java.lang.reflect.Method

import izumi.distage.model.exceptions.MissingRefException
import izumi.distage.model.reflection.universe.RuntimeDIUniverse
import net.sf.cglib.proxy.{MethodInterceptor, MethodProxy}

// we use this to be able to display something for uninitialized proxies
protected[distage] class CglibNullMethodInterceptor(key: RuntimeDIUniverse.DIKey) extends MethodInterceptor {
  override def intercept(o: scala.Any, method: Method, objects: Array[AnyRef], methodProxy: MethodProxy): AnyRef = {
    if (method.getName == "toString" && method.getParameterCount == 0) {
      s"__UninitializedProxy__:$key"
    } else {
      throw new MissingRefException(s"Proxy $key is not yet initialized", Set(key), None)
    }
  }
}
