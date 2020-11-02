package izumi.distage.testkit.distagesuite.compiletime

import com.github.pshirshov.test.plugins.StaticTestMain
import com.github.pshirshov.test2.plugins.Fixture.TestRoleAppMain2
import izumi.distage.framework.{PlanCheck, PlanCheckCompileTime}
import izumi.distage.model.exceptions.{InvalidPlanException, PlanCheckException}
import izumi.distage.roles.test.{TestEntrypoint, TestEntrypointPatchedLeak}
import org.scalatest.GivenWhenThen
import org.scalatest.exceptions.TestFailedException
import org.scalatest.wordspec.AnyWordSpec

final class CompileTimePlanCheckerTest extends AnyWordSpec with GivenWhenThen {

  "Check without config" in {
    PlanCheckCompileTime.checkRoleApp(StaticTestMain, "statictestrole", excludeActivations = "", checkConfig = false).check().throwOnError()
    PlanCheckCompileTime.checkRoleApp(StaticTestMain, "statictestrole", excludeActivations = "test:y", checkConfig = false).check().throwOnError()
  }

  "Check when config & requirements are valid" in {
    PlanCheckCompileTime.checkRoleApp(StaticTestMain, "statictestrole", excludeActivations = "test:y", config = "check-test-good.conf").check().throwOnError()
  }

  "Check depending plugin with plugins" in {
    PlanCheckCompileTime.checkRoleApp(StaticTestMain, "dependingrole", excludeActivations = "test:y", checkConfig = false).check().throwOnError()
    PlanCheckCompileTime.checkRoleApp(StaticTestMain, "dependingrole", excludeActivations = "test:y", config = "check-test-good.conf").check().throwOnError()
  }

  "Check with different activation" in {
    PlanCheckCompileTime.checkRoleApp(StaticTestMain, "statictestrole", excludeActivations = "test:x", config = "check-test-good.conf").check().throwOnError()
  }

  "regression test: can again check when config is false after 1.0" in {
    PlanCheck
      .checkRoleApp(StaticTestMain, "statictestrole", "test:y", "check-test-bad.conf")
      .maybeErrorMessage.exists(_.contains("Expected type NUMBER. Found STRING instead"))

    val err = intercept[TestFailedException] {
      assertCompiles("""PlanCheckCompileTime.checkRoleApp(StaticTestMain, "statictestrole", "test:y", "check-test-bad.conf")""")
    }
    assert(err.getMessage.contains("Expected type NUMBER. Found STRING instead"))
  }

  "Check with invalid role produces error" in {
    assert {
      PlanCheck
        .checkRoleApp(StaticTestMain, "unknownrole")
        .maybeErrorMessage.exists(_.contains("Unknown roles:"))
    }

    val err = intercept[Throwable](assertCompiles("""
      PlanCheckCompileTime.checkRoleApp(StaticTestMain, "unknownrole")
      """.stripMargin))
    assert(err.getMessage.contains("Unknown roles:"))
  }

  "onlyWarn mode does not fail compilation on errors" in {
    assertThrows[PlanCheckException] {
      PlanCheck.checkRoleApp(StaticTestMain, "statictestrole", config = "check-test-bad.conf").throwOnError()
    }
    assertTypeError(
      """
      PlanCheckCompileTime.checkRoleApp(StaticTestMain, "statictestrole", config = "check-test-bad.conf", onlyWarn = false)
      """
    )
    assertCompiles(
      """
      PlanCheckCompileTime.checkRoleApp(StaticTestMain, "statictestrole", config = "check-test-bad.conf", onlyWarn = true)
      """
    )
  }

  "Do not report errors from parts of the graph accessible only from excluded activations" in {
    PlanCheck
      .checkRoleApp(
        TestRoleAppMain2,
        excludeActivations = "mode:test",
      ).throwOnError()

    And("fail without exclusion")
    intercept[PlanCheckException] {
      PlanCheck
        .checkRoleApp(
          TestRoleAppMain2
        ).throwOnError()
    }
  }

  "role app configwriter role passes check" in {
    PlanCheck.checkRoleApp(TestEntrypointPatchedLeak, "configwriter help").throwOnError()
  }

  "role app passes check if `mode:test` activation is excluded and XXX_LocatorLeak is provided in RoleAppMain object" in {
    new PlanCheckCompileTime.Main(
      TestEntrypointPatchedLeak,
      excludeActivations = "mode:test",
      checkConfig = true,
    ).planCheck.check().throwOnError()

    new PlanCheckCompileTime.Main(
      TestEntrypointPatchedLeak,
      excludeActivations = "mode:test",
      checkConfig = false,
    ).planCheck.check().throwOnError()

    assertTypeError(
      """
       new PlanCheckCompileTime.Main(
        TestEntrypointPatchedLeak,
        checkConfig = false,
      ).planCheck.check().throwOnError()
      """
    )

    intercept[PlanCheckException] {
      PlanCheck
        .checkRoleApp(
          TestEntrypointPatchedLeak,
          checkConfig = false,
        ).throwOnError()
    }
  }

  "progression test: role app fails check for excluded compound activations that are equivalent to just excluding `mode:test`" in {
    val issues = PlanCheck
      .checkRoleApp(
        TestEntrypointPatchedLeak,
        excludeActivations = "mode:test axiscomponentaxis:correct | mode:test axiscomponentaxis:incorrect",
      ).maybeError
    assert(issues.isDefined)
  }

  "role app fails config check if config file with insufficient configs is passed" in {
    val errCompile = intercept[TestFailedException](assertCompiles("""
      new PlanCheckCompileTime.Main(
        TestEntrypointPatchedLeak,
        config = "testrole04-reference.conf",
        excludeActivations = "mode:test",
      )
    """))
    assert(errCompile.getMessage.contains("DIConfigReadException"))

    val errRuntime = intercept[PlanCheckException] {
      PlanCheck
        .checkRoleApp(
          TestEntrypointPatchedLeak,
          config = "testrole04-reference.conf",
          excludeActivations = "mode:test",
        )
        .throwOnError()
    }
    assert(errRuntime.getMessage.contains("DIConfigReadException"))
  }

  "role app fails check if XXX_LocatorLeak is missing" in {
    val errCompile = intercept[TestFailedException](assertCompiles("""
    new PlanCheckCompileTime.Main(
      TestEntrypoint,
      config = "checker-test-good.conf",
      excludeActivations = "mode:test",
    )
    """))
    assert(errCompile.getMessage.contains("Required by refs:"))
    assert(errCompile.getMessage.contains("XXX_LocatorLeak"))

    val errRuntime = intercept[PlanCheckException](
      PlanCheck
        .checkRoleApp(
          TestEntrypoint,
          config = "checker-test-good.conf",
          excludeActivations = "mode:test",
        ).throwOnError()
    )
    assert(errRuntime.getMessage.contains("Required by refs:"))
    assert(errRuntime.getMessage.contains("XXX_LocatorLeak"))
  }

  "role app check reports checking the same plugins at runtime as at compile-time" in {
    val runtimePlugins = PlanCheck
      .checkRoleApp(
        TestEntrypointPatchedLeak,
        config = "checker-test-good.conf",
        excludeActivations = "mode:test",
      ).throwOnError()

    val compileTimePlugins = new PlanCheckCompileTime.Main(
      TestEntrypointPatchedLeak,
      config = "checker-test-good.conf",
      excludeActivations = "mode:test",
    ).planCheck.checkedPlugins

    assert(runtimePlugins.result.map(_.getClass).toSet == compileTimePlugins.map(_.getClass).toSet)
  }

}
