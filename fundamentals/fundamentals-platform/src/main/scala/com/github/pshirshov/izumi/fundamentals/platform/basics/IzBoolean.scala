package com.github.pshirshov.izumi.fundamentals.platform.basics

trait IzBoolean {
  final implicit class LazyBool(b: => Boolean) {
    @inline def value: Boolean = b
  }
  @inline final def all(b1: Boolean, b: LazyBool*): Boolean = {
    b1 && b.forall(_.value)
  }

  @inline final def any(b1: Boolean, b: LazyBool*): Boolean = {
    b1 || b.exists(_.value)
  }
}

object IzBoolean extends IzBoolean {

}
