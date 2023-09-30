package izumi.distage.provisioning.strategies.dynamicproxy

import izumi.distage.model.definition.errors.ProvisionerIssue
import izumi.distage.model.provisioning.proxies.ProxyProvider.ProxyParams.{Empty, Params}
import izumi.distage.model.provisioning.proxies.ProxyProvider.{DeferredInit, ProxyContext}
import izumi.distage.model.provisioning.proxies.{DistageProxy, ProxyProvider}
import izumi.distage.model.reflection.DIKey
import izumi.distage.provisioning.strategies.bytebuddyproxy.{ByteBuddyAtomicRefDispatcher, ByteBuddyNullMethodInterceptor}
import net.bytebuddy.ByteBuddy
import net.bytebuddy.description.method.MethodDescription
import net.bytebuddy.dynamic.scaffold.TypeValidation
import net.bytebuddy.implementation.InvocationHandlerAdapter
import net.bytebuddy.matcher.{ElementMatcher, ElementMatchers}

import java.lang.reflect.InvocationHandler

object DynamicProxyProvider extends ProxyProvider {
  def makeCycleProxy(deferredKey: DIKey, proxyContext: ProxyContext): Either[ProvisionerIssue, DeferredInit] = {
    for {
      nullDispatcher <- Right(new ByteBuddyNullMethodInterceptor(deferredKey))
      nullProxy <- mkDynamic(nullDispatcher, proxyContext)
      realDispatcher <- Right(new ByteBuddyAtomicRefDispatcher(nullProxy))
      realProxy <- mkDynamic(realDispatcher, proxyContext)
    } yield {
      DeferredInit(realDispatcher, realProxy)
    }
  }

  private def mkDynamic(dispatcher: InvocationHandler, proxyContext: ProxyContext): Either[ProvisionerIssue, AnyRef] = {
    val clazz = proxyContext.runtimeClass.asInstanceOf[Class[AnyRef]]

    val builder = new ByteBuddy()
      .`with`(TypeValidation.DISABLED)
      .subclass(clazz)
      .method(ElementMatchers.isMethod.asInstanceOf[ElementMatcher[MethodDescription]])
      .intercept(InvocationHandlerAdapter.of(dispatcher))
      .implement(classOf[DistageProxy])
      .make()

    for {
      constructedProxyClass <- {
        try {
          Right(builder.load(this.getClass.getClassLoader)) // works with sbt layered classloader
        } catch {
          case t1: java.lang.NoClassDefFoundError =>
            try {
              Right(builder.load(clazz.getClassLoader)) // works in some other cases (mdoc)
            } catch {
              case t2: Throwable =>
                Left(
                  ProvisionerIssue.ProxyClassloadingFailed(
                    proxyContext,
                    Seq(t1, t2),
                  )
                )
            }
        }
      }.map(_.getLoaded.asInstanceOf[Class[AnyRef]]: Class[AnyRef])
      out <- {
        try {
          proxyContext.params match {
            case Empty =>
              Right(constructedProxyClass.getDeclaredConstructor().newInstance())
            case Params(types, values) =>
              val c = constructedProxyClass.getDeclaredConstructor(types: _*)
              Right(c.newInstance(values.map(_.asInstanceOf[AnyRef]): _*))
          }
        } catch {
          case f: Throwable =>
            Left(
              ProvisionerIssue.ProxyInstantiationFailed(
                proxyContext,
                f,
              )
            )

//            throw new ProxyInstantiationException(
//              s"Failed to instantiate class with ByteBuddy, make sure you don't dereference proxied parameters in constructors: " +
//              s"class=${proxyContext.runtimeClass}, params=${proxyContext.params}, exception=${f.stacktraceString}",
//              clazz,
//              proxyContext.params,
//              proxyContext.op,
//              f,
//            )
        }
      }
    } yield {
      out
    }
  }
}
