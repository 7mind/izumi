package izumi.distage.testkit.distagesuite.compiletime

import com.github.pshirshov.test.plugins.StaticTestMain
import izumi.distage.framework.PerformPlanCheck
import org.scalatest.exceptions.TestFailedException
import org.scalatest.wordspec.AnyWordSpec

class StaticPluginCheckerTest extends AnyWordSpec {

  "Check without config" in {
    PerformPlanCheck.bruteforce(StaticTestMain, "statictestrole", "", checkConfig = false)
    PerformPlanCheck.bruteforce(StaticTestMain, "statictestrole", "test:x", checkConfig = false)
  }

  "Check when config & requirements are valid" in {
    PerformPlanCheck.bruteforce(StaticTestMain, "statictestrole", "test:x", "check-test-good.conf")
  }

  "Check depending plugin with plugins" in {
    PerformPlanCheck.bruteforce(StaticTestMain, "dependingrole", "test:x", checkConfig = false)
    PerformPlanCheck.bruteforce(StaticTestMain, "dependingrole", "test:x", "check-test-good.conf")
  }

  "Check with different activation" in {
    PerformPlanCheck.bruteforce(StaticTestMain, "statictestrole", "test:y", "check-test-good.conf")
  }

  "regression test: can again check when config is false after 1.0" in {
    val err = intercept[TestFailedException] {
      assertCompiles("""PerformPlanCheck.bruteforce(StaticTestMain, "statictestrole", "test:x", "check-test-bad.conf")""")
    }
    assert(err.getMessage().contains("Expected type NUMBER. Found STRING instead"))
  }

  "Check with invalid axis produces error" in {
    val err = intercept[Throwable](assertCompiles("""
      PerformPlanCheck.bruteforce(StaticTestMain, "statictestrole", "missing:axis", "check-test-good.conf")
      """.stripMargin))
    assert(err.getMessage.contains("Unknown axis: missing"))
  }

  "onlyWarn mode does not fail compilation on errors" in {
    assertCompiles(
      """
      PerformPlanCheck.bruteforce(StaticTestMain, "statictestrole", "missing:axis", "check-test-good.conf", onlyWarn = true)
        """
    )
  }

}
