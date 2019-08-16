package izumi.fundamentals.reflection

import izumi.fundamentals.platform.console.TrivialLogger
import izumi.fundamentals.platform.console.TrivialLogger.Config

import scala.reflect.ClassTag
import scala.reflect.macros.blackbox

/**
* To see macro debug output during compilation, set `-Dizumi.distage.debug.macro=true` java property! e.g.
* {{{
* sbt -Dizumi.distage.debug.macro=true compile
* }}}
*/
object TrivialMacroLogger {
  def id(s: String) = s"izumi.debug.macro.$s"
  def make[T: ClassTag](c: blackbox.Context, id: String): TrivialLogger =
    TrivialLogger.make[T](id, config = Config(sink = new ScalacSink(c)))
}
