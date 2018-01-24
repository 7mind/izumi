package org.bitbucket.pshirshov.izumi.distage.provisioning.cglib

import net.sf.cglib.proxy.Callback
import org.bitbucket.pshirshov.izumi.distage.TypeFull
import org.bitbucket.pshirshov.izumi.distage.model.exceptions.DIException
import org.bitbucket.pshirshov.izumi.distage.model.plan.ExecutableOp

import scala.util.{Failure, Success, Try}


object CglibTools {

  def mkdynamic[T](dispatcher: Callback, fullType: TypeFull, runtimeClass: Class[_], t: ExecutableOp)(mapper: AnyRef => T): T = {
    val enhancer = new InitializingEnhancer(fullType, runtimeClass)
    enhancer.setSuperclass(runtimeClass)
    enhancer.setCallback(dispatcher)

    Try(enhancer.create()) match {
      case Success(proxyInstance) =>
        mapper(proxyInstance)

      case Failure(f) =>
        throw new DIException(s"Failed to instantiate abstract class with CGLib. Operation: $t", f)
    }
  }

}
