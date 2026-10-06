package izumi.distage.testkit.runner.spec

import scala.compiletime.testing.typeCheckErrors

trait FrontendAssertions { self: TestAssertions =>
  transparent inline def assertCompiles(inline code: String): Assertion = {
    val errors = typeCheckErrors(code)
    if (errors.nonEmpty) fail("Expected compilation to succeed:\n" + errors.map(_.message).mkString("\n"))
  }

  transparent inline def assertDoesNotCompile(inline code: String): Assertion = {
    val errors = typeCheckErrors(code)
    if (errors.isEmpty) fail("Expected compilation to fail:\n" + code)
  }
}
