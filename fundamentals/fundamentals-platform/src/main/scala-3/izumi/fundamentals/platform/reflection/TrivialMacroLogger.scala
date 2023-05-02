package izumi.fundamentals.platform.reflection

import izumi.fundamentals.platform.console.TrivialLogger
import izumi.fundamentals.platform.console.TrivialLogger.Config

import scala.quoted.Quotes
import scala.reflect.ClassTag

/**
  * To see macro debug output during compilation, set `-Dizumi.debug.macro.rtti=true` java property! e.g.
  *
  * {{{
  * sbt -Dizumi.debug.macro.rtti=true compile
  * }}}
  *
  * @see [[izumi.reflect.DebugProperties]]
  */
object TrivialMacroLogger {
  def make[T: ClassTag](sysProperty: String)(using qctx: Quotes): TrivialLogger = {
    TrivialLogger.make[T](sysProperty, config = Config(sink = new ScalacSink(qctx)))
  }
}
