package com.github.pshirshov.izumi.fundamentals.reflection

//case class EqualitySafeType(tpe: TypeNative) {
//
//  override def toString: String = tpe.toString
//
//  override def hashCode(): Int = tpe.toString.hashCode
//
//  override def equals(obj: scala.Any): Boolean = obj match {
//    case EqualitySafeType(otherSymbol) =>
//      tpe =:= otherSymbol
//    case _ =>
//      false
//  }
//}

object EqualitySafeType {
  def get[T: RuntimeUniverse.Tag] = RuntimeUniverse.SafeType.get[T] //EqualitySafeType(typeTag[T].tpe)
}



