package com.github.pshirshov.izumi.fundamentals.platform.basics

import com.github.pshirshov.izumi.fundamentals.platform.basics.IzBoolean.LazyBool

import scala.language.implicitConversions

trait IzBoolean {
  @inline implicit final def LazyBool(b: => Boolean): LazyBool = new LazyBool(() => b)

  @inline final def all(b1: Boolean, b: LazyBool*): Boolean = {
    b1 && b.forall(_.value)
  }

  @inline final def any(b1: Boolean, b: LazyBool*): Boolean = {
    b1 || b.exists(_.value)
  }
}

object IzBoolean extends IzBoolean {
  @inline final implicit class LazyBool(private val b: () => Boolean) extends AnyVal {
    @inline def value: Boolean = b()
  }
}
