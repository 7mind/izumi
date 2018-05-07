package com.github.pshirshov.izumi.distage.provisioning.cglib

import java.lang.invoke.MethodHandles
import java.lang.reflect.Method

import com.github.pshirshov.izumi.distage.model.exceptions.DIException
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp
import net.sf.cglib.proxy.{Callback, Enhancer}

import scala.util.{Failure, Success, Try}


sealed trait ProxyParams
object ProxyParams {
  case object Empty extends ProxyParams
  final case class Params(types: Array[Class[_]], values: Array[AnyRef]) extends ProxyParams

}

object CglibTools {

  def mkDynamic[T](dispatcher: Callback, runtimeClass: Class[_], op: ExecutableOp, params: ProxyParams)(callback: AnyRef => T): T = {
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
        callback(proxyInstance)

      case Failure(f) =>
        throw new DIException(s"Failed to instantiate class $runtimeClass, params=$params with CGLib. Operation: $op", f)
    }
  }

  def invokeExistingMethod(o: Any, method: Method, objects: Array[AnyRef]): AnyRef = {
    CglibTools.TRUSTED_METHOD_HANDLES
      .in(method.getDeclaringClass)
      .unreflectSpecial(method, method.getDeclaringClass)
      .bindTo(o)
      .invokeWithArguments(objects: _*)
  }


  final val TRUSTED_METHOD_HANDLES = {
    val methodHandles = classOf[MethodHandles.Lookup].getDeclaredField("IMPL_LOOKUP")
    methodHandles.setAccessible(true)
    methodHandles.get(null).asInstanceOf[MethodHandles.Lookup]
  }
}
