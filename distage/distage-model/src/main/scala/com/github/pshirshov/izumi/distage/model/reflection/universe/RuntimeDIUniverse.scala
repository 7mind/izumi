package com.github.pshirshov.izumi.distage.model.reflection.universe

import com.github.pshirshov.izumi.distage.model.definition.DIStageAnnotation
import com.github.pshirshov.izumi.fundamentals.platform.functional.Identity

trait RuntimeDIUniverse extends DIUniverse {
  override final val u: scala.reflect.runtime.universe.type = scala.reflect.runtime.universe

  class IdContractImpl[T] extends IdContract[T] {
    override def repr(value: T): String = value.toString
  }

  override implicit val stringIdContract: IdContract[String] = new IdContractImpl[String]
  override implicit def singletonStringIdContract[S <: String with Singleton]: IdContract[S] = new IdContractImpl[S]

  implicit def anyIdContract[T]: IdContract[T] = new IdContractImpl[T]

  override protected val typeOfDistageAnnotation: SafeType = SafeType.get[DIStageAnnotation]

  val identityEffectType: SafeType = SafeType.getK[Identity]
}

object RuntimeDIUniverse extends RuntimeDIUniverse
