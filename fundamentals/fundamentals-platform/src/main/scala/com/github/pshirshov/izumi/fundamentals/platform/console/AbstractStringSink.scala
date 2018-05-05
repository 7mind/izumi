package com.github.pshirshov.izumi.fundamentals.platform.console

trait AbstractStringSink {
  def flush(value: => String): Unit
}

object NullStringSink extends AbstractStringSink {
  override def flush(value: => String): Unit = {} // Don't use discard here, triggered computation is expensive
}

object SystemOutStringSink extends AbstractStringSink {
  override def flush(value: => String): Unit = println(value)
}

object SystemErrStringSink extends AbstractStringSink {
  override def flush(value: => String): Unit = println(value)
}
