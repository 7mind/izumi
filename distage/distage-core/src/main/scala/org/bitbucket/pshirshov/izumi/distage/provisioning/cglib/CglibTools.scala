package org.bitbucket.pshirshov.izumi.distage.provisioning.cglib

import net.sf.cglib.proxy.{Callback, Enhancer}
import org.bitbucket.pshirshov.izumi.distage.model.exceptions.DIException
import org.bitbucket.pshirshov.izumi.distage.model.plan.ExecutableOp

import scala.util.{Failure, Success, Try}



object CglibTools {
  final val IZ_INIT_METHOD_NAME = "$__IZUMI__INIT__$"


  def mkdynamic[T](t: ExecutableOp, enhancer: Enhancer, dispatcher: Callback, runtimeClass: Class[_])(mapper: AnyRef => T): T = {

    enhancer.setSuperclass(runtimeClass)
    enhancer.setCallback(dispatcher)

    Try(enhancer.create()) match {
      case Success(proxyInstance) =>
        mapper(proxyInstance)

      case Failure(f) =>
        throw new DIException(s"Failed to instantiate abstract class with CGLib. Operation: $t", f)
    }
  }


  def initTrait(instance: AnyRef): Unit = {

    val ret = instance.getClass.getMethod(IZ_INIT_METHOD_NAME)
      .invoke(instance)

    if (!ret.asInstanceOf[Boolean]) {
      throw new DIException("Self-check failed, trait initializer didn't return `true`", null)
    }
  }
}
