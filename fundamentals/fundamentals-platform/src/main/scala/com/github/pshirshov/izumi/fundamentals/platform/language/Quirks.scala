package com.github.pshirshov.izumi.fundamentals.platform.language

object Quirks {
  def discard(t: Any*): Unit = {
    t match {
      case _ =>
        ()
    }
  }
}
