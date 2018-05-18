package com.github.pshirshov.izumi.distage.model.reflection.universe

trait RuntimeDIUniverse extends DIUniverse {
  override final val u: scala.reflect.runtime.universe.type = scala.reflect.runtime.universe

  val mirror: u.Mirror = scala.reflect.runtime.currentMirror

  class IdContractImpl[T] extends IdContract[T] {
    override def repr(value: T): String = value.toString
  }

  override implicit def stringIdContract: IdContract[String] = new IdContractImpl[String]
  override implicit def singletonStringIdContract[S <: String with Singleton]: IdContract[S] = new IdContractImpl[S]

  implicit def anyIdContract[T]: IdContract[T] = new IdContractImpl[T]
}

object RuntimeDIUniverse extends RuntimeDIUniverse
