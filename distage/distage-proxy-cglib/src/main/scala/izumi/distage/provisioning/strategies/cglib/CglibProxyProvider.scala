package izumi.distage.provisioning.strategies.cglib

import java.lang.invoke.MethodHandles
import java.lang.reflect.Method

import izumi.distage.model.provisioning.strategies
import izumi.distage.model.provisioning.strategies._
import izumi.distage.model.reflection.universe.MirrorProvider
import izumi.distage.provisioning.strategies.cglib.exceptions.CgLibInstantiationOpException
import net.sf.cglib.proxy.{Callback, Enhancer}

import scala.util.{Failure, Success, Try}

class CglibProxyProvider(mirrorProvider: MirrorProvider) extends ProxyProvider {
  override def makeCycleProxy(cycleContext: CycleContext, proxyContext: ProxyContext): DeferredInit = {
    val nullDispatcher = new CglibNullMethodInterceptor(cycleContext.deferredKey)
    val nullProxy = mkDynamic(nullDispatcher, proxyContext)
    val dispatcher = new CglibRefDispatcher(cycleContext.deferredKey, nullProxy)
    val proxy = mkDynamic(dispatcher, proxyContext)
    strategies.DeferredInit(dispatcher, proxy)
  }

  private def mkDynamic(dispatcher: Callback, proxyContext: ProxyContext): AnyRef = {
    import proxyContext._
    val enhancer = new Enhancer()

    // Enhancer.setSuperclass is sideffectful, so we had to copypaste
    Option(runtimeClass) match {
      case Some(value) if value.isInterface =>
        enhancer.setInterfaces(Array[Class[_]](value, classOf[DistageProxy]))
      case Some(value) if value == classOf[Any] =>
        enhancer.setInterfaces(Array(classOf[DistageProxy]))
      case Some(value) =>
        enhancer.setSuperclass(value)
        enhancer.setInterfaces(Array(classOf[DistageProxy]))
      case None =>
        enhancer.setSuperclass(null)
        enhancer.setInterfaces(Array(classOf[DistageProxy]))
    }


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
        throw new CgLibInstantiationOpException(
          s"Failed to instantiate class with CGLib, make sure you don't use proxied parameters in constructors: class=$runtimeClass, params=$params, exception=${f.getMessage}", runtimeClass, params, op, f)
    }
  }
}

object CglibProxyProvider {
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
