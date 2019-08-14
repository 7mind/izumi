package com.github.pshirshov.izumi.fundamentals.reflection

import com.github.pshirshov.izumi.fundamentals.platform.console.AbstractStringTrivialSink

import scala.reflect.macros.blackbox

class ScalacSink(c: blackbox.Context) extends AbstractStringTrivialSink {
  override def flush(value: => String): Unit = c.info(c.enclosingPosition, value, force = true)
}
