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

  "Check with invalid axis produces error" ignore {
    PlanCheck
      .checkRoleApp(StaticTestMain, "statictestrole", "missing:axis", "check-test-good.conf")
      .issues.exists(_.getMessage.contains("Unknown axis: missing"))

    val err = intercept[Throwable](assertCompiles("""
      PerformPlanCheck.bruteforce(StaticTestMain, "statictestrole", "missing:axis", "check-test-good.conf")
      """.stripMargin))
    assert(err.getMessage.contains("Unknown axis: missing"))
  }

  "onlyWarn mode does not fail compilation on errors" in {
    assertThrows[InvalidPlanException] {
      PlanCheck.checkRoleApp(StaticTestMain, "statictestrole", config = "check-test-bad.conf").throwOnError()
    }
    assertCompiles(
      """
      PerformPlanCheck.bruteforce(StaticTestMain, "statictestrole", config = "check-test-bad.conf", onlyWarn = true)
        """
    )
  }

  "progression test: check role app module" in assertThrows[TestFailedException] {
    PlanCheck.checkRoleApp(TestEntrypointPatchedLeak, "configwriter help")

//    PlanCheck.checkRoleApp(
//      TestEntrypointPatchedLeak,
//      "testtask00 testrole01 testrole02 testrole03 testrole04",
//      "mode:prod axiscomponentaxis:correct | mode:prod axiscomponentaxis:incorrect",
//    )

//    new PerformPlanCheck.Main(
//      TestEntrypointPatchedLeak,
//      "testtask00 testrole01 testrole02 testrole03 testrole04",
//      activations = "mode:prod axiscomponentaxis:correct | mode:prod axiscomponentaxis:incorrect",
//    ).planCheck.rerunAtRuntime()

//    new PerformPlanCheck.Main(
//      TestEntrypointPatchedLeak,
//      activations = "mode:prod axiscomponentaxis:correct | mode:prod axiscomponentaxis:incorrect",
//      checkConfig = false,
//    ).planCheck.rerunAtRuntime()
//
//
//    assertThrows[Throwable] {
//      new PerformPlanCheck.Main(
//        TestEntrypointPatchedLeak,
//        config = "testrole04-reference.conf",
//        activations = "mode:prod axiscomponentaxis:correct | mode:prod axiscomponentaxis:incorrect",
//      ).planCheck.run()
//    }

    val err1 = intercept[TestFailedException](assertCompiles("""
    new PerformPlanCheck.Main(
      izumi.distage.roles.test.TestEntrypoint,
      config = "checker-test-good.conf",
      activations = "mode:prod axiscomponentaxis:correct | mode:prod axiscomponentaxis:incorrect",
      printPlan = true,
    )
      """))
    assert(err1.getMessage.contains("Plan was:"))
    assert(err1.getMessage.contains("{type.TestRole03[=λ %0 → IO[+0]]}"))

    val err2 = intercept[TestFailedException](assertCompiles("""
    new PerformPlanCheck.Main(
      izumi.distage.roles.test.TestEntrypoint,
      config = "checker-test-good.conf",
      activations = "mode:prod axiscomponentaxis:correct | mode:prod axiscomponentaxis:incorrect",
    )
    """))
    assert(!err2.getMessage.contains("Plan was:"))
    assert(!err2.getMessage.contains("{type.TestRole03[=λ %0 → IO[+0]]}"))

    val runtimePlugins = PlanCheck.checkRoleApp(
      TestEntrypointPatchedLeak,
      config = "checker-test-good.conf",
      activations = "mode:prod axiscomponentaxis:correct | mode:prod axiscomponentaxis:incorrect",
    )
    val compileTimePlugins = new PerformPlanCheck.Main(
      TestEntrypointPatchedLeak,
      config = "checker-test-good.conf",
      activations = "mode:prod axiscomponentaxis:correct | mode:prod axiscomponentaxis:incorrect",
    ).planCheck.checkedPlugins

    assert(runtimePlugins.throwOnError().result.map(_.getClass).toSet == compileTimePlugins.map(_.getClass).toSet)

//      new PerformPlanCheck.Main(
//        TestEntrypointPatchedLeak,
//        "testtask00 testrole01 testrole02 testrole03 testrole04",
//      ).planCheck.run()

//      PlanCheck.checkRoleApp(
//        TestEntrypointPatchedLeak,
//        "testtask00 testrole01 testrole02 testrole03 testrole04",
//        "mode:test axiscomponentaxis:correct | mode:test axiscomponentaxis:incorrect",
//      )
  }

}
