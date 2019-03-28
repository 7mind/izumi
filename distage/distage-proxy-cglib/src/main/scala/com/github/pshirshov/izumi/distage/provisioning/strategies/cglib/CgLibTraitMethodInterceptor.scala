package com.github.pshirshov.izumi.distage.provisioning.strategies.cglib

import java.lang.reflect.Method

import com.github.pshirshov.izumi.distage.model.exceptions.MissingRefException
import com.github.pshirshov.izumi.distage.model.provisioning.ProvisioningKeyProvider
import com.github.pshirshov.izumi.distage.model.provisioning.strategies.TraitIndex
import net.sf.cglib.proxy.{MethodInterceptor, MethodProxy}

import scala.collection.mutable

protected[distage] class CgLibTraitMethodInterceptor
(
  index: TraitIndex
  , context: ProvisioningKeyProvider
) extends MethodInterceptor {

  private val fields = mutable.HashMap[String, Any]()

  override def intercept(o: scala.Any, method: Method, objects: Array[AnyRef], methodProxy: MethodProxy): AnyRef = {
    //premature optimization, all our methods are parameterless
    if (method.getParameterTypes.length == 0 && index.methods.contains(method)) {
      val wireWith = index.methods(method).wireWith

      context.fetchKey(wireWith, makeByName = false) match {
        case Some(v) =>
          v.asInstanceOf[AnyRef]

        case None =>
          throw new MissingRefException(s"Cannot return $wireWith from ${method.getName}, it's not available in the object graph o_O", Set(wireWith), None)
      }

    } else if (index.getters.contains(method.getName)) {
      fields.synchronized {
        val field = index.getters(method.getName).name
        fields.getOrElse(field, throw new NullPointerException(s"Field $field was not initialized for $o"))
      }.asInstanceOf[AnyRef]
    } else if (index.setters.contains(method.getName)) {
      fields.synchronized {
        fields.put(index.setters(method.getName).name, objects.head)
      }.asInstanceOf[AnyRef]
    } else {
      CglibProxyProvider.invokeExistingMethod(o, method, objects)
    }
  }

}
