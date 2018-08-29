package com.github.pshirshov.izumi.fundamentals.reflection

import scala.reflect.runtime.{universe => ru}
import ReflectionUtil._

// TODO: hotspots, hashcode on keys is inefficient
class SafeType0[U <: SingletonUniverse](tpe: U#Type) {
  private final val dealiased: U#Type = {
    deannotate(tpe.dealias)
  }

  override final lazy val toString: String = {
    dealiased.toString
  }

  /**
  * Workaround for Type's hashcode being unstable with sbt fork := false and version of scala other than 2.12.4
  * (two different scala-reflect libraries end up on classpath and lead to undefined hashcode)
  * */
  override final val hashCode: Int = {
    dealiased.typeSymbol.name.toString.hashCode
  }

  override final def equals(obj: scala.Any): Boolean = obj match {
    case other: SafeType0[U] @unchecked =>
      dealiased =:= other.dealiased
    case _ =>
      false
  }

  final def <:<(that: SafeType0[U]): Boolean =
    dealiased <:< that.dealiased
}

object SafeType0 {
  def apply[U <: SingletonUniverse](tpe: U#Type): SafeType0[U] = new SafeType0(tpe)

  def apply[T: ru.TypeTag]: SafeType0[ru.type] = new SafeType0(ru.typeOf[T])
}
