package izumi.fundamentals.assertions

trait Assertions {
  inline def assert(inline condition: Boolean): Unit = ${ AssertionMacro.expand('condition, '{ AssertionContext.standard }, '{ this }) }
  inline def assert(inline condition: Boolean, inline context: AssertionContext): Unit = ${ AssertionMacro.expand('condition, 'context, '{ this }) }
}

object Assert extends Assertions
