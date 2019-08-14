package izumi.fundamentals.reflection

import izumi.fundamentals.platform.console.AbstractStringTrivialSink

import scala.reflect.macros.blackbox

class MacroTrivialSink(c: blackbox.Context) extends AbstractStringTrivialSink {
  override def flush(value: => String): Unit =
    c.info(c.enclosingPosition, value, force = true)
}

