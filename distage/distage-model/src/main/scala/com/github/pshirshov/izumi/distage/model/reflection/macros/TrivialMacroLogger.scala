package com.github.pshirshov.izumi.distage.model.reflection.macros

import com.github.pshirshov.izumi.fundamentals.platform.console.TrivialLogger
import com.github.pshirshov.izumi.fundamentals.reflection.MacroTrivialSink

import scala.reflect.ClassTag
import scala.reflect.macros.blackbox

/**
* To see macro debug output during compilation, set `-Dizumi.distage.debug.macro=true` java property! i.e.
*
*   sbt -Dizumi.distage.debug.macro=true compile
*/
object TrivialMacroLogger {
  def apply[T: ClassTag](c: blackbox.Context): TrivialLogger =
    TrivialLogger.make[T]("izumi.distage.debug.macro", sink = new MacroTrivialSink(c))
}
