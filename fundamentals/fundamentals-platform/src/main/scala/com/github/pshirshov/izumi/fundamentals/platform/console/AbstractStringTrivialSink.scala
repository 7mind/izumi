package com.github.pshirshov.izumi.fundamentals.platform.console

trait AbstractStringTrivialSink {
  def flush(value: => String): Unit
}

object NullStringTrivialSink extends AbstractStringTrivialSink {
  override def flush(value: => String): Unit = {} // Don't use discard here, triggered computation is expensive
}

object SystemOutStringTrivialSink extends AbstractStringTrivialSink {
  override def flush(value: => String): Unit = System.out.println(value)
}

object SystemErrStringTrivialSink extends AbstractStringTrivialSink {
  override def flush(value: => String): Unit = System.err.println(value)
}
