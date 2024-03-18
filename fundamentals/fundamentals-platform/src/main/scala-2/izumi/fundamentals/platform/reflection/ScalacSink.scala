package izumi.fundamentals.reflection

import izumi.fundamentals.platform.console.AbstractStringTrivialSink

import scala.reflect.macros.blackbox

final class ScalacSink(c: blackbox.Context) extends AbstractStringTrivialSink {
  override def flush(value: => String): Unit = {
    c.info(c.enclosingPosition, value, force = true)
  }

  override def flushError(value: => String): Unit = {
    c.info(c.enclosingPosition, value, force = true)
  }
}
