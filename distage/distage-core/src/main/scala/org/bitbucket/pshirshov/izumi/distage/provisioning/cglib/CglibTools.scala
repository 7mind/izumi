package org.bitbucket.pshirshov.izumi.distage.provisioning.cglib

import net.sf.cglib.proxy.{Callback, Enhancer}
import org.bitbucket.pshirshov.izumi.distage.model.exceptions.DIException
import org.bitbucket.pshirshov.izumi.distage.model.plan.ExecutableOp
import org.bitbucket.pshirshov.izumi.distage.provisioning.OpResult

import scala.util.{Failure, Success, Try}

object CglibTools {
  def dynamic(t: ExecutableOp, dispatcher: Callback, runtimeClass: Class[_]): Seq[OpResult] = {
    mkdynamic(t, dispatcher, runtimeClass) {
      proxyInstance =>
        Seq(OpResult.NewInstance(t.target, proxyInstance))
    }
  }

  def mkdynamic[T](t: ExecutableOp, dispatcher: Callback, runtimeClass: Class[_])(mapper: AnyRef => T): T = {
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
}
