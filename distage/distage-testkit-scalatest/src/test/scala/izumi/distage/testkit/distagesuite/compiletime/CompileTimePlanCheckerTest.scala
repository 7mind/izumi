package izumi.distage.testkit.distagesuite.compiletime

import com.github.pshirshov.test.plugins.StaticTestMain
import izumi.distage.framework.{PerformPlanCheck, PlanCheck}
import izumi.distage.model.exceptions.InvalidPlanException
import izumi.distage.roles.test.TestEntrypointPatchedLeak
import org.scalatest.exceptions.TestFailedException
import org.scalatest.wordspec.AnyWordSpec

final class CompileTimePlanCheckerTest extends AnyWordSpec {

  "Check without config" in {
    PerformPlanCheck.bruteforce(StaticTestMain, "statictestrole", "", checkConfig = false).check().throwOnError()
    PerformPlanCheck.bruteforce(StaticTestMain, "statictestrole", "test:x", checkConfig = false).check().throwOnError()
  }

  "Check when config & requirements are valid" in {
    PerformPlanCheck.bruteforce(StaticTestMain, "statictestrole", "test:x", "check-test-good.conf").check().throwOnError()
  }

  "Check depending plugin with plugins" in {
    PerformPlanCheck.bruteforce(StaticTestMain, "dependingrole", "test:x", checkConfig = false).check().throwOnError()
    PerformPlanCheck.bruteforce(StaticTestMain, "dependingrole", "test:x", "check-test-good.conf").check().throwOnError()
  }

  "Check with different activation" in {
    PerformPlanCheck.bruteforce(StaticTestMain, "statictestrole", "test:y", "check-test-good.conf").check().throwOnError()
  }

  "regression test: can again check when config is false after 1.0" in {
    PlanCheck
      .checkRoleApp(StaticTestMain, "statictestrole", "test:x", "check-test-bad.conf")
      .issues.exists(_.getMessage.contains("Expected type NUMBER. Found STRING instead"))

    val err = intercept[TestFailedException] {
      assertCompiles("""PerformPlanCheck.bruteforce(StaticTestMain, "statictestrole", "test:x", "check-test-bad.conf")""")
    }
    assert(err.getMessage.contains("Expected type NUMBER. Found STRING instead"))
  }

  "Check with invalid role produces error" in {
    assert {
      PlanCheck
        .checkRoleApp(StaticTestMain, "unknownrole")
        .issues.exists(_.getMessage.contains("Unknown roles:"))
    }

    val err = intercept[Throwable](assertCompiles("""
      PerformPlanCheck.bruteforce(StaticTestMain, "unknownrole")
      """.stripMargin))
    assert(err.getMessage.contains("Unknown roles:"))
  }

  "onlyWarn mode does not fail compilation on errors" in {
    assertThrows[InvalidPlanException] {
      PlanCheck.checkRoleApp(StaticTestMain, "statictestrole", config = "check-test-bad.conf").throwOnError()
    }
    assertTypeError(
      """
      PerformPlanCheck.bruteforce(StaticTestMain, "statictestrole", config = "check-test-bad.conf", onlyWarn = false)
        """
    )
    assertCompiles(
      """
      PerformPlanCheck.bruteforce(StaticTestMain, "statictestrole", config = "check-test-bad.conf", onlyWarn = true)
        """
    )
  }

  // split into meaningful tests
  "check role app module" in {
    val c0 = intercept[Throwable](assertCompiles("""
      new PerformPlanCheck.Main(
        TestEntrypointPatchedLeak,
        config = "testrole04-reference.conf",
        excludeActivations = "mode:test",
      )
    """))
    assert(c0.getMessage.contains("DIConfigReadException"))

    val err0 = intercept[Throwable] {
      PlanCheck
        .checkRoleApp(
          TestEntrypointPatchedLeak,
          config = "testrole04-reference.conf",
          excludeActivations = "mode:test",
        )
        .throwOnError()
    }
    assert(err0.getMessage.contains("DIConfigReadException"))

    PlanCheck.checkRoleApp(TestEntrypointPatchedLeak, "configwriter help").throwOnError()

    PlanCheck
      .checkRoleApp(
        TestEntrypointPatchedLeak,
        "testtask00 testrole01 testrole02 testrole03 testrole04",
        "mode:test",
      ).throwOnError()

    new PerformPlanCheck.Main(
      TestEntrypointPatchedLeak,
      "testtask00 testrole01 testrole02 testrole03 testrole04",
      excludeActivations = "mode:test",
    ).planCheck.check().throwOnError()

    new PerformPlanCheck.Main(
      TestEntrypointPatchedLeak,
      excludeActivations = "mode:test",
      checkConfig = false,
    ).planCheck.check().throwOnError()

    val err1 = intercept[TestFailedException](assertCompiles("""
    new PerformPlanCheck.Main(
      izumi.distage.roles.test.TestEntrypoint,
      config = "checker-test-good.conf",
      excludeActivations = "mode:test",
    )
    """))
    assert(err1.getMessage.contains("Required by refs:"))
    assert(err1.getMessage.contains("XXX_LocatorLeak"))

    val runtimePlugins = PlanCheck
      .checkRoleApp(
        TestEntrypointPatchedLeak,
        config = "checker-test-good.conf",
        excludeActivations = "mode:test",
      ).throwOnError()

    val compileTimePlugins = new PerformPlanCheck.Main(
      TestEntrypointPatchedLeak,
      config = "checker-test-good.conf",
      excludeActivations = "mode:test",
    ).planCheck.checkedPlugins

    assert(runtimePlugins.result.map(_.getClass).toSet == compileTimePlugins.map(_.getClass).toSet)

//    new PerformPlanCheck.Main(
//      TestEntrypointPatchedLeak,
//      "testtask00 testrole01 testrole02 testrole03 testrole04",
//    ).planCheck.check().throwOnError()

//    PlanCheck
//      .checkRoleApp(
//        TestEntrypointPatchedLeak,
//        "testtask00 testrole01 testrole02 testrole03 testrole04",
//        activations = "mode:test axiscomponentaxis:correct | mode:test axiscomponentaxis:incorrect",
//      ).throwOnError()
  }

}
