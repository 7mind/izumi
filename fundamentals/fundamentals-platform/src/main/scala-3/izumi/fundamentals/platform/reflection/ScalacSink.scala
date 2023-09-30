package izumi.fundamentals.platform.reflection

import izumi.fundamentals.platform.console.AbstractStringTrivialSink

import scala.quoted.Quotes

final class ScalacSink(qctx: Quotes) extends AbstractStringTrivialSink {
  override def flush(value: => String): Unit = {
    qctx.reflect.report.info(value)
  }

  override def flushError(value: => String): Unit = {
    qctx.reflect.report.info(value)
  }
}
