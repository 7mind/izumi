package izumi.distage.model.reflection.macros

import izumi.fundamentals.platform.console.TrivialLogger
import izumi.fundamentals.reflection.MacroTrivialSink

import scala.reflect.ClassTag
import scala.reflect.macros.blackbox

/**
* To see macro debug output during compilation, set `-Dizumi.distage.debug.macro=true` java property! e.g.
* {{{
* sbt -Dizumi.distage.debug.macro=true compile
* }}}
*/
object TrivialMacroLogger {
  def apply[T: ClassTag](c: blackbox.Context): TrivialLogger =
    TrivialLogger.make[T]("izumi.distage.debug.macro", sink = new MacroTrivialSink(c))
}
