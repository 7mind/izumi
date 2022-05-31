package izumi.distage.provisioning.strategies.bytebuddyproxy

import izumi.distage.model.exceptions.interpretation.ProxyInstantiationException
import izumi.distage.model.provisioning.proxies.ProxyProvider
import izumi.distage.model.provisioning.proxies.ProxyProvider.ProxyParams.{Empty, Params}
import izumi.distage.model.provisioning.proxies.ProxyProvider.{DeferredInit, ProxyContext}
import izumi.distage.model.reflection.DIKey
import izumi.fundamentals.platform.exceptions.IzThrowable.*
import net.bytebuddy.ByteBuddy
import net.bytebuddy.implementation.InvocationHandlerAdapter

import java.lang.reflect.InvocationHandler

object DynamicProxyProvider extends ProxyProvider {

  override def makeCycleProxy(deferredKey: DIKey, proxyContext: ProxyContext): DeferredInit = {
    val nullDispatcher = new CglibNullMethodInterceptor(deferredKey)
    val nullProxy = mkDynamic(nullDispatcher, proxyContext)

    val realDispatcher = new CglibAtomicRefDispatcher(nullProxy)
    val realProxy = mkDynamic(realDispatcher, proxyContext)

    DeferredInit(realDispatcher, realProxy)
  }

  private def mkDynamic(dispatcher: InvocationHandler, proxyContext: ProxyContext): AnyRef = {
    val clazz = proxyContext.runtimeClass.asInstanceOf[Class[AnyRef]]

    val constructedProxyClass: Class[AnyRef] = new ByteBuddy()
      .subclass(clazz)
      .implement(classOf[DistageProxy])
      .intercept(InvocationHandlerAdapter.of(dispatcher))
      .make()
      .load(clazz.getClassLoader)
      .getLoaded.asInstanceOf[Class[AnyRef]]

    try {
      proxyContext.params match {
        case Empty =>
          constructedProxyClass.getDeclaredConstructor().newInstance()
        case Params(types, values) =>
          val c = constructedProxyClass.getDeclaredConstructor(types: _*)
          c.newInstance(values: _*)
      }
    } catch {
      case f: Throwable =>
        throw new ProxyInstantiationException(
          s"Failed to instantiate class with CGLib, make sure you don't dereference proxied parameters in constructors: " +
          s"class=${proxyContext.runtimeClass}, params=${proxyContext.params}, exception=${f.stackTrace}",
          clazz,
          proxyContext.params,
          proxyContext.op,
          f,
        )
    }
  }
}
