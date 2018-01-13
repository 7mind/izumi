package org.bitbucket.pshirshov.izumi.di.model

case class Value[A](value: A) {
  @inline final def map[B](f: A => B): Value[B] =
    Value(f(this.value))
}
