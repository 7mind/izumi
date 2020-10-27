package izumi.distage.testkit.distagesuite.compiletime

import com.github.pshirshov.test.plugins.StaticTestMain
import izumi.distage.framework.{PlanCheck, PlanCheckCompileTime}
import izumi.distage.model.exceptions.InvalidPlanException
import izumi.distage.roles.test.{TestEntrypoint, TestEntrypointPatchedLeak}
import org.scalatest.exceptions.TestFailedException
import org.scalatest.wordspec.AnyWordSpec

final class CompileTimePlanCheckerTest extends AnyWordSpec {

  "Check without config" in {
    PlanCheckCompileTime.checkRoleApp(StaticTestMain, "statictestrole", "", checkConfig = false).check().throwOnError()
    PlanCheckCompileTime.checkRoleApp(StaticTestMain, "statictestrole", "test:x", checkConfig = false).check().throwOnError()
  }

  "Check when config & requirements are valid" in {
    PlanCheckCompileTime.checkRoleApp(StaticTestMain, "statictestrole", "test:x", "check-test-good.conf").check().throwOnError()
  }

  "Check depending plugin with plugins" in {
    PlanCheckCompileTime.checkRoleApp(StaticTestMain, "dependingrole", "test:x", checkConfig = false).check().throwOnError()
    PlanCheckCompileTime.checkRoleApp(StaticTestMain, "dependingrole", "test:x", "check-test-good.conf").check().throwOnError()
  }

  "Check with different activation" in {
    PlanCheckCompileTime.checkRoleApp(StaticTestMain, "statictestrole", "test:y", "check-test-good.conf").check().throwOnError()
  }

  "regression test: can again check when config is false after 1.0" in {
    PlanCheck
      .checkRoleApp(StaticTestMain, "statictestrole", "test:x", "check-test-bad.conf")
      .issues.exists(_.getMessage.contains("Expected type NUMBER. Found STRING instead"))

    val err = intercept[TestFailedException] {
      assertCompiles("""PlanCheckCompileTime.checkRoleApp(StaticTestMain, "statictestrole", "test:x", "check-test-bad.conf")""")
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
      PlanCheckCompileTime.checkRoleApp(StaticTestMain, "unknownrole")
      """.stripMargin))
    assert(err.getMessage.contains("Unknown roles:"))
  }

  "onlyWarn mode does not fail compilation on errors" in {
    assertThrows[InvalidPlanException] {
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

  "role app configwriter role passes check" in {
    PlanCheck.checkRoleApp(TestEntrypointPatchedLeak, "configwriter help").throwOnError()
  }

  "role app passes check if `mode:test` activation is excluded and XXX_LocatorLeak is provided in RoleAppMain object" in {
    new PlanCheckCompileTime.Main(
      TestEntrypointPatchedLeak,
      "testtask00 testrole01 testrole02 testrole03 testrole04",
      "mode:test",
    ).planCheck.check().throwOnError()

    new PlanCheckCompileTime.Main(
      TestEntrypointPatchedLeak,
      "testtask00 testrole01 testrole02 testrole03 testrole04",
      excludeActivations = "mode:test",
      checkConfig = true,
    ).planCheck.check().throwOnError()

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

    val errRuntime = intercept[InvalidPlanException](
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

  "role app fails config check if config file with insufficient configs is passed" in {
    val errCompile = intercept[Throwable](assertCompiles("""
      new PlanCheckCompileTime.Main(
        TestEntrypointPatchedLeak,
        config = "testrole04-reference.conf",
        excludeActivations = "mode:test",
      )
    """))
    assert(errCompile.getMessage.contains("DIConfigReadException"))

    val errRuntime = intercept[InvalidPlanException] {
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
