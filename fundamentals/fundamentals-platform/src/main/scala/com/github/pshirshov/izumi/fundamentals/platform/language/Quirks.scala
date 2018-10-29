package com.github.pshirshov.izumi.fundamentals.platform.language

object Quirks {
  @inline def discard(trash: LazyDiscarder[_]*): Unit = {
    val _ = trash
  }

  implicit final class LazyDiscarder[T](t: => T) {
    @inline def discard(): Unit = Quirks.discard(t)
  }
}
