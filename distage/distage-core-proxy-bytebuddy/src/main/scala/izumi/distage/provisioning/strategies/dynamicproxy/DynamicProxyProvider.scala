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

    val builder = new ByteBuddy()
      .`with`(TypeValidation.DISABLED)
      .subclass(clazz)
      .method(ElementMatchers.isMethod.asInstanceOf[ElementMatcher[MethodDescription]])
      .intercept(InvocationHandlerAdapter.of(dispatcher))
      .implement(classOf[DistageProxy])
      .make()

    val constructedProxyClass: Class[AnyRef] = {
      (try {
        builder.load(this.getClass.getClassLoader) // works with sbt layered classloader
      } catch {
        case t1: java.lang.NoClassDefFoundError =>
          try {
            builder.load(clazz.getClassLoader) // works in some other cases (mdoc)
          } catch {
            case t2: Throwable =>
              throw new ProxyInstantiationException(
                s"Failed to load proxy class with ByteBuddy " +
                s"class=${proxyContext.runtimeClass}, params=${proxyContext.params}\n\n" +
                s"exception 1(DynamicProxyProvider classLoader)=${t1.stackTrace}\n\n" +
                s"exception 2(classloader of class)=${t2.stackTrace}",
                clazz,
                proxyContext.params,
                proxyContext.op,
                t2,
              )
          }
      }).getLoaded.asInstanceOf[Class[AnyRef]]
    }

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
          s"Failed to instantiate class with ByteBuddy, make sure you don't dereference proxied parameters in constructors: " +
          s"class=${proxyContext.runtimeClass}, params=${proxyContext.params}, exception=${f.stackTrace}",
          clazz,
          proxyContext.params,
          proxyContext.op,
          f,
        )
    }
  }
}
