package izumi.distage.provisioning.strategies.dynamicproxy

import izumi.distage.model.exceptions.interpretation.ProxyInstantiationException
import izumi.distage.model.provisioning.proxies.ProxyProvider.ProxyParams.{Empty, Params}
import izumi.distage.model.provisioning.proxies.ProxyProvider.{DeferredInit, ProxyContext}
import izumi.distage.model.provisioning.proxies.{DistageProxy, ProxyProvider}
import izumi.distage.model.reflection.DIKey
import izumi.distage.provisioning.strategies.bytebuddyproxy.{ByteBuddyAtomicRefDispatcher, ByteBuddyNullMethodInterceptor}
import izumi.fundamentals.platform.exceptions.IzThrowable.*
import net.bytebuddy.ByteBuddy
import net.bytebuddy.description.method.MethodDescription
import net.bytebuddy.dynamic.scaffold.TypeValidation
import net.bytebuddy.implementation.InvocationHandlerAdapter
import net.bytebuddy.matcher.{ElementMatcher, ElementMatchers}

import java.lang.reflect.InvocationHandler

object DynamicProxyProvider extends ProxyProvider {

  override def makeCycleProxy(deferredKey: DIKey, proxyContext: ProxyContext): DeferredInit = {
    val nullDispatcher = new ByteBuddyNullMethodInterceptor(deferredKey)
    val nullProxy = mkDynamic(nullDispatcher, proxyContext)

    val realDispatcher = new ByteBuddyAtomicRefDispatcher(nullProxy)
    val realProxy = mkDynamic(realDispatcher, proxyContext)

    DeferredInit(realDispatcher, realProxy)
  }

  private def mkDynamic(dispatcher: InvocationHandler, proxyContext: ProxyContext): AnyRef = {
    val clazz = proxyContext.runtimeClass.asInstanceOf[Class[AnyRef]]

    val cl = this.getClass.getClassLoader

    val constructedProxyClass: Class[AnyRef] = new ByteBuddy()
      .`with`(TypeValidation.DISABLED)
      .subclass(clazz)
      .method(ElementMatchers.isMethod.asInstanceOf[ElementMatcher[MethodDescription]])
      .intercept(InvocationHandlerAdapter.of(dispatcher))
      .implement(classOf[DistageProxy])
      .make()
      .load(cl)
      .getLoaded.asInstanceOf[Class[AnyRef]]

    try {
      proxyContext.params match {
        case Empty =>
          constructedProxyClass.getDeclaredConstructor().newInstance()
        case Params(types, values) =>
          val c = constructedProxyClass.getDeclaredConstructor(types: _*)
          c.newInstance(values.map(_.asInstanceOf[AnyRef]): _*)
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
