package com.github.pshirshov.izumi.fundamentals.platform.language

object Quirks {
  def discard(t: Any*): Unit = {
    t match {
      case _ =>
        ()
    }
  }

  implicit class Discarder[T](t: T) extends AnyRef {
    def discard(): Unit = Quirks.discard(t)
  }
}
