package com.github.pshirshov.izumi.distage.commons

import java.lang.reflect.InvocationTargetException

import com.github.pshirshov.izumi.distage.TypeFull
import com.github.pshirshov.izumi.distage.model.exceptions.TraitInitializationFailedException

object TraitTools {
  //        try {
  //          CglibTools.initTrait(instance)
  //        } catch {
  //          case e: AbstractMethodError =>
  //            System.err.println(e)
  //
  //          case e: NoSuchMethodError =>
  //            System.err.println(e)
  //
  //        }

  def initTrait(instanceType: TypeFull, runtimeClass: Class[_], instance: AnyRef): Unit = {
    instanceType.tpe.decls.find(_.name.decodedName.toString == "$init$") match {
      case Some(_) => // here we have an instance of scala MethodSymbol though we can't reflect it, so let's use java
        try {
          try {
            runtimeClass
              .getDeclaredMethod("$init$", runtimeClass)
              .invoke(instance, instance)
            ()
          } catch {
            case e: InvocationTargetException =>
              throw e.getCause
          }
        } catch {
          case e: AbstractMethodError =>
            throw new TraitInitializationFailedException(s"TODO: Failed to initialize trait $instanceType. Probably it contains fields (val or var) though fields are not supported yet, see https://github.com/pshirshov/izumi-r2/issues/26", instanceType, e)

          case e: Throwable =>
            throw new TraitInitializationFailedException(s"Failed to initialize trait $instanceType. It may be an issue with the trait, framework bug or trait instantiator implemetation lim", instanceType, e)
        }
      case None =>
    }
  }
}
