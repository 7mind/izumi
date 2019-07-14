package com.github.pshirshov.izumi.functional

final class Value[A] private (private val value: A) extends AnyVal {
  @inline def map[B](f: A => B): Value[B] = {
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
