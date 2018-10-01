package com.github.pshirshov.izumi.fundamentals.reflection

import com.github.pshirshov.izumi.fundamentals.reflection.ReflectionUtil._

import scala.reflect.runtime.{universe => ru}

// TODO: hotspots, hashcode on keys is inefficient
class SafeType0[U <: SingletonUniverse](val tpe: U#Type) {

  import SafeType0._

  private final val dealiased: U#Type = {
    deannotate(tpe.dealias)
  }

  override final lazy val toString: String = {
    dealiased.toString
  }

  private final val prefix: Option[NonUniqueSingleTypeWrapper[U]] = {
    if (dealiased.typeSymbol.isStatic || dealiased == scala.reflect.runtime.universe.NoPrefix) {
      None
    } else {
      dealiased
        .asInstanceOf[scala.reflect.runtime.universe.TypeRefApi]
        .pre.asInstanceOf[AnyRef] match {
        case u: scala.reflect.internal.Types#SingleType =>
          Some(NonUniqueSingleTypeWrapper[U](u.pre.asInstanceOf[U#Type], u.sym.asInstanceOf[U#Symbol]))

        case _ =>
          None
      }
    }
  }

  /**
    * Workaround for Type's hashcode being unstable with sbt fork := false and version of scala other than 2.12.4
    * (two different scala-reflect libraries end up on classpath and lead to undefined hashcode)
    **/
  override final val hashCode: Int = {
    if (prefix.isDefined) {
      prefix.hashCode()
    } else {
      dealiased.typeSymbol.name.toString.hashCode

    }
  }

  override final def equals(obj: Any): Boolean = {
    obj match {
      case other: SafeType0[U]@unchecked =>
        toString == other.toString
//        val eq = (prefix.isDefined && prefix == other.prefix && toString == other.toString) || dealiased =:= other.dealiased
//        println(s"$this <?> $obj == $eq; $prefix , ${other.prefix}")
//        eq
      case _ =>
        false
    }
  }

  final def <:<(that: SafeType0[U]): Boolean =
    dealiased <:< that.dealiased

  final def weak_<:<(that: SafeType0[U]): Boolean =
    dealiased weak_<:< that.dealiased
}

object SafeType0 {

  case class NonUniqueSingleTypeWrapper[U <: SingletonUniverse](pre: U#Type, sym: U#Symbol) {
    override def equals(obj: Any): Boolean = {
      obj match {
        case other: NonUniqueSingleTypeWrapper[U] =>
          val eq = SafeType0(pre) == SafeType0(other.pre) && sym.toString == other.sym.toString
          println((pre, other.pre, sym, other.sym, eq))

          eq
        case _ =>
          false
      }
    }

    override def hashCode(): Int = {
      pre.toString.hashCode ^ sym.toString.hashCode
    }
  }

  def apply[U <: SingletonUniverse](tpe: U#Type): SafeType0[U] = new SafeType0(tpe)

  def get[T: ru.TypeTag]: SafeType0[ru.type] = new SafeType0(ru.typeOf[T])
}
