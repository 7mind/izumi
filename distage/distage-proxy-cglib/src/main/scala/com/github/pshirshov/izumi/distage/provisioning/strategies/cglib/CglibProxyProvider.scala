package com.github.pshirshov.izumi.distage.provisioning.strategies.cglib

import java.lang.invoke.MethodHandles
import java.lang.reflect.Method

import com.github.pshirshov.izumi.distage.model.exceptions.DIException
import com.github.pshirshov.izumi.distage.model.provisioning.strategies
import com.github.pshirshov.izumi.distage.model.provisioning.strategies._
import net.sf.cglib.proxy.{Callback, Enhancer}

import scala.util.{Failure, Success, Try}


object CglibProxyProvider extends ProxyProvider {
  override def makeCycleProxy(cycleContext: CycleContext, proxyContext: ProxyContext): DeferredInit = {
    val nullDispatcher = new CglibNullMethodInterceptor(cycleContext.deferredKey)
    val nullProxy = CglibProxyProvider.mkDynamic(nullDispatcher, proxyContext)
    val dispatcher = new CglibRefDispatcher(nullProxy)
    val proxy = CglibProxyProvider.mkDynamic(dispatcher, proxyContext)
    strategies.DeferredInit(dispatcher, proxy)
  }

  override def makeTraitProxy(factoryContext: TraitContext, proxyContext: ProxyContext): AnyRef = {
    val dispatcher = new CgLibTraitMethodInterceptor(factoryContext.index, factoryContext.context)
    mkDynamic(dispatcher, proxyContext)
  }

  override def makeFactoryProxy(factoryContext: FactoryContext, proxyContext: ProxyContext): AnyRef = {
    import factoryContext._
    val dispatcher = new CgLibFactoryMethodInterceptor(
      factoryMethodIndex
      , dependencyMethodIndex
      , narrowedContext
      , executor
      , op
    )

    mkDynamic(dispatcher, proxyContext)
  }

  private def mkDynamic(dispatcher: Callback, proxyContext: ProxyContext): AnyRef = {
    import proxyContext._
    val enhancer = new Enhancer()
    enhancer.setSuperclass(runtimeClass)
    enhancer.setCallback(dispatcher)

    val result = params match {
      case ProxyParams.Empty =>
        Try(enhancer.create())

      case ProxyParams.Params(types, values) =>
        Try(enhancer.create(types, values))
    }

    result match {
      case Success(proxyInstance) =>
        proxyInstance

      case Failure(f) =>
        throw new DIException(s"Failed to instantiate class $runtimeClass, params=$params with CGLib. Operation: $op", f)
    }
  }

  protected[cglib] def invokeExistingMethod(o: Any, method: Method, objects: Array[AnyRef]): AnyRef = {
    CglibProxyProvider.TRUSTED_METHOD_HANDLES
      .in(method.getDeclaringClass)
      .unreflectSpecial(method, method.getDeclaringClass)
      .bindTo(o)
      .invokeWithArguments(objects: _*)
  }


  private final lazy val TRUSTED_METHOD_HANDLES = {
    val methodHandles = classOf[MethodHandles.Lookup].getDeclaredField("IMPL_LOOKUP")
    methodHandles.setAccessible(true)
    methodHandles.get(null).asInstanceOf[MethodHandles.Lookup]
  }
}

