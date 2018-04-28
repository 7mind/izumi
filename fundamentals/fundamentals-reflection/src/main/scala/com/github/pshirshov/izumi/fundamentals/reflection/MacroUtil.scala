package com.github.pshirshov.izumi.fundamentals.reflection

import com.github.pshirshov.izumi.fundamentals.platform.console.{AbstractStringSink, TrivialLogger}

import scala.reflect.ClassTag
import scala.reflect.macros.blackbox

object MacroUtil {

  class MacroSink(c: blackbox.Context) extends AbstractStringSink {
    override def flush(value: => String): Unit =
      c.info(c.enclosingPosition, value, force = true)
  }

  def mkLogger[T: ClassTag](c: blackbox.Context): TrivialLogger =
    TrivialLogger.make[T]("izumi.debug.distage.macro", sink = new MacroSink(c))

}
