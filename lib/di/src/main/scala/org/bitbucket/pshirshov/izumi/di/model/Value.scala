package org.bitbucket.pshirshov.izumi.di.model

class Value[A] private(value: A) {
  @inline final def map[B](f: A => B): Value[B] = {
    new Value(f(this.value))
  }

  @inline def eff(f: A => Unit): Value[A] = {
    f(value)
    this
  }

  @inline def get: A = value
}

object Value {
  def apply[A](value: A): Value[A] = new Value[A](value)
}

//
//
//sealed trait ValueOp
//
//object ValueOp {
//  case class Set[A](value: A) extends ValueOp
//  case class Eff[A](f: A => Unit) extends ValueOp
//  case class Map[A, B](f: A => B) extends ValueOp
//}
//
//final class Value[A] private (ops: Seq[ValueOp]) {
//  @inline def map[B](f: A => B): Value[B] = {
//    new Value[B](ops :+ ValueOp.Map[A, B](f))
//  }
//
//  @inline def eff(f: A => Unit): Value[A] = {
//    new Value[A](ops :+ ValueOp.Eff[A](f))
//  }
//
//  def get: A = {
//    ops.tail.fold(ops.head.asInstanceOf[ValueOp.Set[_]].value) {
//      case (acc, op: ValueOp.Map[Any, Any]) =>
//        op.f(acc).asInstanceOf[A]
//
//      case (acc, op: ValueOp.Eff[Any]) =>
//        op.f(acc)
//        acc
//    }.asInstanceOf[A]
//  }
//}
//
//object Value {
//  def apply[A](value: A): Value[A] = new Value[A](Seq(ValueOp.Set[A](value)))
//}
