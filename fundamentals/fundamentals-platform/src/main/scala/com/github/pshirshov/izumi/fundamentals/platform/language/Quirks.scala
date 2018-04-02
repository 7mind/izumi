package com.github.pshirshov.izumi.fundamentals.platform.language

object Quirks {
  def discard(t: AnyRef*): Unit = {
    t match {
      case _ =>
        ()
    }
  }
}
