package com.github.pshirshov.izumi.fundamentals.platform.language

object Quirks {
  def discard(t: Any*): Unit = {
    t match {
      case _ =>
        ()
    }
  }

  implicit final class Discarder[T](private val t: T) extends AnyVal {
    def discard(): Unit = Quirks.discard(t)
  }
}
