package com.github.pshirshov.izumi.distage.model.reflection.universe

import com.github.pshirshov.izumi.distage.model.definition.DIStageAnnotation

trait RuntimeDIUniverse extends DIUniverse {
  override final val u: scala.reflect.runtime.universe.type = scala.reflect.runtime.universe

  class IdContractImpl[T] extends IdContract[T] {
    override def repr(value: T): String = value.toString
  }

  override implicit def stringIdContract: IdContract[String] = new IdContractImpl[String]
  override implicit def singletonStringIdContract[S <: String with Singleton]: IdContract[S] = new IdContractImpl[S]

  implicit def anyIdContract[T]: IdContract[T] = new IdContractImpl[T]

  override protected val typeOfDistageAnnotation: SafeType = SafeType.get[DIStageAnnotation]
}

object RuntimeDIUniverse extends RuntimeDIUniverse
