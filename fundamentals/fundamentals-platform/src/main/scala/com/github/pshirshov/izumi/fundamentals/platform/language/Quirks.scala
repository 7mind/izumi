package com.github.pshirshov.izumi.fundamentals.platform.language

import scala.language.implicitConversions

/**
  * Syntax for explicitly discarding values to satisfy -Ywarn-value-discard,
  * and for clarity of course!
  **/
object Quirks {

  @inline final def discard(trash: Any*): Unit = {
    val _ = trash
  }

  @inline final def forget(trash: LazyDiscarder[_, _]*): Unit = {
    val _ = trash
  }

  @inline implicit final class Discarder[T](private val t: T) extends AnyVal {
    @inline def discard(): Unit = {
      val _ = t
    }
  }

  @inline implicit final def LazyDiscarder[T](t: => T): LazyDiscarder[T, Unit] = {
    def _x: T = { _x; t }
    new LazyDiscarder[T, Unit]()
  }

  @inline final class LazyDiscarder[T, U >: Unit](private val dummy: Boolean = false) extends AnyVal {
    @inline def forget: U = ()
  }

}
