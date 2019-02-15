package com.github.pshirshov.izumi.fundamentals.platform.language

import scala.language.implicitConversions

object Quirks {

  @inline def discard(trash: Any*): Unit = {
    val _ = trash
  }

  @inline def forget(trash: LazyDiscarder[_, _]*): Unit = {
    val _ = trash
  }

  implicit final class Discarder[T](private val t: T) extends AnyVal {
    @inline def discard(): Unit = {
      val _ = t
    }
  }

  @inline implicit def LazyDiscarder[T](t: => T): LazyDiscarder[T, Unit] = {
    def _x: T = { _x; t }
    new LazyDiscarder[T, Unit]()
  }

  final class LazyDiscarder[T, U >: Unit](private val dummy: Boolean = false) extends AnyVal {
    @inline def forget: U = ()
  }

}
