package com.github.pshirshov.izumi.fundamentals.platform.language

object Quirks {
  object Lazy {
    @inline def discard(trash: LazyDiscarder[_]*): Unit = {
      val _ = trash
    }

    implicit final class LazyDiscarder[T](t: => T) {
      @inline def discard(): Unit = Quirks.Lazy.discard(t)
    }
  }

  @inline def discard(trash: Any*): Unit = {
    val _ = trash
  }

  implicit final class Discarder[T](t: T) {
    @inline def discard(): Unit = Quirks.discard(t)
  }
}
