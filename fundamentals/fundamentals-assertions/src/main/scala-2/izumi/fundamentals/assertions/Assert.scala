package izumi.fundamentals.assertions

import scala.language.experimental.macros

trait Assertions {
  def assert(condition: Boolean): Unit = macro AssertionMacro.standard
  def assert(condition: Boolean, context: AssertionContext): Unit = macro AssertionMacro.configured
}

object Assert extends Assertions
