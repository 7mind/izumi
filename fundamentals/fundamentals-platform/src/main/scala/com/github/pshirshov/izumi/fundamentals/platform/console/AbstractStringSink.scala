package com.github.pshirshov.izumi.fundamentals.platform.console

import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks

trait AbstractStringSink {
  def flush(value: => String): Unit
}

object NullStringSink extends AbstractStringSink {
  override def flush(value: => String): Unit = Quirks.discard(value)
}

object SystemOutStringSink extends AbstractStringSink {
  override def flush(value: => String): Unit = println(value)
}

object SystemErrStringSink extends AbstractStringSink {
  override def flush(value: => String): Unit = println(value)
}
