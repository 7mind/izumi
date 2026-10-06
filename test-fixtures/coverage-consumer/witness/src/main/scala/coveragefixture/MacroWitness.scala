package coveragefixture

import izumi.fundamentals.assertions.{Assert, AssertionFailure}

object MacroWitness {
  def check(value: Int): Unit = Assert.assert(value == 42, "coverage macro")
  def exercise(): Unit = {
    check(42)
    val failure = try { check(-1); None } catch { case value: AssertionFailure => Some(value) }
    require(failure.nonEmpty)
    println("COVERAGE_MACRO_WITNESS success-and-failure")
  }
}
