package izumi.distage.provisioning.strategies.cglibproxy

import izumi.distage.model.exceptions.interpretation.MissingRefException

import java.lang.reflect.Method
import izumi.distage.model.reflection.DIKey
import net.sf.cglib.proxy.{MethodInterceptor, MethodProxy}

// we use this to be able to display something for uninitialized proxies
private[distage] class CglibNullMethodInterceptor(
  key: DIKey
) extends MethodInterceptor {
  override def intercept(o: Any, method: Method, objects: Array[AnyRef], methodProxy: MethodProxy): AnyRef = {
    if (method.getName == "toString" && method.getParameterCount == 0) {
      s"__UninitializedProxy__:$key"
    } else {
      throw new MissingRefException(s"Proxy for $key is not yet initialized", Set(key), None)
    }
  }
}
