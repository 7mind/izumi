package org.bitbucket.pshirshov.izumi.distage.provisioning.cglib

import java.lang.invoke.MethodHandles
import java.lang.reflect.Method

import net.sf.cglib.proxy.{MethodInterceptor, MethodProxy}
import org.bitbucket.pshirshov.izumi.distage.model.exceptions.MissingRefException
import org.bitbucket.pshirshov.izumi.distage.model.plan.Association
import org.bitbucket.pshirshov.izumi.distage.provisioning.ProvisioningContext

protected[distage] class CgLibTraitMethodInterceptor
(
  index: Map[Method, Association.Method]
  , context: ProvisioningContext
) extends MethodInterceptor {

  override def intercept(o: scala.Any, method: Method, objects: Array[AnyRef], methodProxy: MethodProxy): AnyRef = {
    //premature optimization, all our methods are parameterless
    if (method.getParameterTypes.length == 0 && index.contains(method)) {
      val wireWith = index(method).wireWith

      context.fetchKey(wireWith) match {
        case Some(v) =>
          v.asInstanceOf[AnyRef]

        case None =>
          throw new MissingRefException(s"Cannot return $wireWith from ${method.getName}, it's not available in the context o_O", Set(wireWith), None)
      }

    } else {
      CgLibTraitMethodInterceptor.TRUSTED_METHOD_HANDLES
        .in(method.getDeclaringClass)
        .unreflectSpecial(method, method.getDeclaringClass)
        .bindTo(o)
        .invokeWithArguments(objects: _*)
    }
  }
}

object CgLibTraitMethodInterceptor {
  private val METHOD_HANDLES_WORKAROUND = classOf[MethodHandles.Lookup].getDeclaredField("IMPL_LOOKUP")
  METHOD_HANDLES_WORKAROUND.setAccessible(true)

  final val TRUSTED_METHOD_HANDLES = METHOD_HANDLES_WORKAROUND.get(null).asInstanceOf[MethodHandles.Lookup]
}
