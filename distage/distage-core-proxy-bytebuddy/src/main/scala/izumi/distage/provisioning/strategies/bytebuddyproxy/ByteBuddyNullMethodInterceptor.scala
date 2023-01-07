package izumi.distage.provisioning.strategies.bytebuddyproxy

import izumi.distage.model.exceptions.runtime.MissingRefException
import izumi.distage.model.reflection.DIKey

import java.lang.reflect.{InvocationHandler, Method}

// we use this to be able to display something for uninitialized proxies
private[distage] class ByteBuddyNullMethodInterceptor(
  key: DIKey
) extends InvocationHandler {
  override def invoke(o: Any, method: Method, objects: Array[AnyRef]): AnyRef = {
    if (method.getName == "toString" && method.getParameterCount == 0) {
      s"__UninitializedProxy__:$key"
    } else {
      throw new MissingRefException(s"Proxy for $key is not yet initialized", Set(key), None)
    }
  }
}
