package org.bitbucket.pshirshov.izumi.distage.provisioning.cglib

import java.lang.invoke.MethodHandles
import java.lang.reflect.Method

import net.sf.cglib.proxy.{Callback, Enhancer}
import org.bitbucket.pshirshov.izumi.distage.model.exceptions.DIException
import org.bitbucket.pshirshov.izumi.distage.model.plan.ExecutableOp

import scala.util.{Failure, Success, Try}


object CglibTools {

  def mkdynamic[T](dispatcher: Callback, runtimeClass: Class[_], t: ExecutableOp)(mapper: AnyRef => T): T = {
    val enhancer = new Enhancer()
    enhancer.setSuperclass(runtimeClass)
    enhancer.setCallback(dispatcher)

    Try(enhancer.create()) match {
      case Success(proxyInstance) =>
        mapper(proxyInstance)

      case Failure(f) =>
        throw new DIException(s"Failed to instantiate abstract class with CGLib. Operation: $t", f)
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
