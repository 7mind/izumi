package com.github.pshirshov.izumi.fundamentals.reflection

import scala.reflect.api.Universe

trait DIUniverse {
  val u: Universe
  val mirror: u.Mirror

  case class SafeType(tpe: TypeNative) {

    override def toString: String = tpe.toString

    override def hashCode(): Int = tpe.toString.hashCode

    override def equals(obj: scala.Any): Boolean = obj match {
      case SafeType(other) =>
        tpe =:= other
      case _ =>
        false
    }
  }

  object SafeType {
    def get[T: Tag]: TypeFull = SafeType(u.typeTag[T].tpe)
  }


  type TypeFull = SafeType
  type Tag[T] = u.TypeTag[T]
  type TypeNative = u.Type
  type TypeSymb = u.Symbol
  type MethodSymb = u.MethodSymbol

}
