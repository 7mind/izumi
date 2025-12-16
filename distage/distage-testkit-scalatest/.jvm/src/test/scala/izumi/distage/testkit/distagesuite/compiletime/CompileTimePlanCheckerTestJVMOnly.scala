package izumi.distage.testkit.distagesuite.compiletime

import com.github.pshirshov.test.plugins.StaticTestMain
import com.github.pshirshov.test3.bootstrap.BootstrapFixture3.BasicConfig
import com.github.pshirshov.test3.plugins.Fixture3
import izumi.distage.framework.model.exceptions.PlanCheckException
import izumi.distage.framework.{PlanCheck, PlanCheckConfig}
import izumi.distage.model.planning.{AxisPoint, PlanIssue}
import izumi.distage.model.reflection.DIKey
import izumi.distage.roles.test.{TestEntrypoint, TestEntrypointPatchedLeak}
import izumi.fundamentals.collections.nonempty.NESet
import izumi.fundamentals.platform.language.literals.{LiteralBoolean, LiteralString}
import org.scalatest.exceptions.TestFailedException
import org.scalatest.wordspec.AnyWordSpec

final class CompileTimePlanCheckerTestJVMOnly extends AnyWordSpec {

  "Check when config & requirements are valid" in {
    PlanCheck
      .assertAppCompileTime(
        StaticTestMain,
        PlanCheckConfig("statictestrole", excludeActivations = "test:y", config = "check-test-good.conf"),
      ).assertAgainAtRuntime()
  }

  "Check depending plugin with plugins" in {
    PlanCheck
      .assertAppCompileTime(
        StaticTestMain,
        PlanCheckConfig("dependingrole", excludeActivations = "test:y", config = "check-test-good.conf"),
      ).assertAgainAtRuntime()
    PlanCheck
      .assertAppCompileTime(StaticTestMain, PlanCheckConfig("dependingrole", excludeActivations = "test:y", checkConfig = false)).assertAgainAtRuntime()
  }

  "Check with different activation" in {
    PlanCheck
      .assertAppCompileTime(
        StaticTestMain,
        PlanCheckConfig("statictestrole", excludeActivations = "test:x", config = "check-test-good.conf"),
      ).assertAgainAtRuntime()
  }

  "regression test: can again check when config is false after 1.0" in {
    PlanCheck.runtime
      .checkApp(StaticTestMain, PlanCheckConfig("statictestrole", "test:y", "check-test-bad.conf"))
      .maybeErrorMessage.exists(_.contains("Expected type NUMBER. Found STRING instead"))

    val err = intercept[TestFailedException] {
      assertCompiles("""
      PlanCheck.assertAppCompileTime(StaticTestMain, PlanCheckConfig("statictestrole", "test:y", "check-test-bad.conf"))
      """)
    }
    assert(err.getMessage.contains("Expected type NUMBER. Found STRING instead"))
  }

  "Check config parsing in bootstrap plugins" in {
    PlanCheck.assertAppCompileTime(Fixture3.TestRoleAppMain)
    PlanCheck.runtime.assertApp(Fixture3.TestRoleAppMain)

    // fail on bad config
    assert(
      intercept[TestFailedException](
        assertCompiles(
          """
          PlanCheck.assertAppCompileTime(Fixture3.TestRoleAppMain, PlanCheckConfig(config = "common-reference.conf"))
          """
        )
      ).getMessage contains "cannot parse configuration"
    )
    val err = intercept[PlanCheckException] {
      PlanCheck.runtime.assertApp(Fixture3.TestRoleAppMain, PlanCheckConfig(config = "common-reference.conf"))
    }
    assert(err.getMessage contains "basicConfig")
    assert(err.issues.get.size == 1)
    assert(err.issues.get.head.asInstanceOf[PlanIssue.UnparseableConfigBinding].key == DIKey[BasicConfig])
  }

  "role app configwriter role passes check" in {
    PlanCheck.runtime.assertApp(TestEntrypointPatchedLeak, PlanCheckConfig("configwriter help"))
  }

  "role app passes check if `mode:test` activation is excluded and XXX_LocatorLeak is provided in RoleAppMain object" in {
    new PlanCheck.Main(
      TestEntrypointPatchedLeak,
      PlanCheckConfig(
        "* -failingrole01 -failingrole02",
        "mode:test",
        checkConfig = true,
      ),
    ).planCheck.assertAgainAtRuntime()

    class b
      extends PlanCheck.Main(
        TestEntrypointPatchedLeak,
        PlanCheckConfig(
          roles = LiteralString("* -failingrole01 -failingrole02"),
          excludeActivations = LiteralString("mode:test"),
          checkConfig = LiteralBoolean(false),
        ),
      )
    new b().planCheck.assertAgainAtRuntime()

    assertTypeError(
      """
      new PlanCheck.Main(
          TestEntrypointPatchedLeak,
        PlanCheckConfig(
          roles = "* -failingrole01 -failingrole02",
          checkConfig = false,
        )
      ).planCheck.check().throwOnError()
      """
    )

    intercept[PlanCheckException] {
      PlanCheck.runtime.assertApp(
        TestEntrypointPatchedLeak,
        PlanCheckConfig(roles = "* -failingrole01 -failingrole02", checkConfig = false),
      )
    }
  }

  "role app fails config check if config file with insufficient configs is passed" in {
    val errCompile = intercept[TestFailedException](assertCompiles("""
      new PlanCheck.Main(
          TestEntrypointPatchedLeak,
        PlanCheckConfig(
          config = "testrole04-reference.conf",
          excludeActivations = "mode:test",
        )
      )
    """))
    assert(errCompile.getMessage.contains("DIConfigReadException"))

    val errRuntime = intercept[PlanCheckException] {
      PlanCheck.runtime.assertApp(
        TestEntrypointPatchedLeak,
        PlanCheckConfig(config = "testrole04-reference.conf", excludeActivations = "mode:test"),
      )
    }
    assert(errRuntime.getMessage.contains("DIConfigReadException"))
  }

  "role app fails check if XXX_LocatorLeak is missing" in {
    val errCompile = intercept[TestFailedException](assertCompiles("""
    new PlanCheck.Main(
       TestEntrypoint,
       PlanCheckConfig(
       config = "checker-test-good.conf",
       excludeActivations = "mode:test",
     )
    )
    """))
    assert(errCompile.getMessage.contains("Required by refs:"))
    assert(errCompile.getMessage.contains("XXX_LocatorLeak"))

    val errRuntime = intercept[PlanCheckException](
      PlanCheck.runtime.assertApp(
        TestEntrypoint,
        PlanCheckConfig(
          config = "checker-test-good.conf",
          excludeActivations = "mode:test",
        ),
      )
    )
    assert(errRuntime.getMessage.contains("Required by refs:"))
    assert(errRuntime.getMessage.contains("XXX_LocatorLeak"))
  }

  "role app check reports checking the same plugins at runtime as at compile-time" in {
    val result = PlanCheck.runtime.checkApp(
      TestEntrypointPatchedLeak,
      PlanCheckConfig(
        roles = "* -failingrole01 -failingrole02",
        config = "checker-test-good.conf",
        excludeActivations = "mode:test",
      ),
    )
    val runtimePlugins = result.checkedPlugins
    result.throwOnError()

    val compileTimePlugins = new PlanCheck.Main(
      TestEntrypointPatchedLeak,
      PlanCheckConfig(
        roles = "* -failingrole01 -failingrole02",
        config = "checker-test-good.conf",
        excludeActivations = "mode:test",
      ),
    ).planCheck.checkedPlugins

    assert(runtimePlugins.result.map(_.getClass).toSet == compileTimePlugins.map(_.getClass).toSet)
  }

  "progression test: role app fails check for excluded compound activations that are equivalent to just excluding `mode:test`" in {
    val res = PlanCheck.runtime.checkApp(
      TestEntrypointPatchedLeak,
      PlanCheckConfig(
        roles = "* -failingrole01 -failingrole02",
        excludeActivations = "mode:test axiscomponentaxis:correct | mode:test axiscomponentaxis:incorrect",
      ),
    )
    assert(res.verificationFailed)
    assert(res.maybeError.get.isRight)
    assert(res.issues.fromNESet.forall {
      case PlanIssue.UnsaturatedAxis(_, _, missingAxisValues) => missingAxisValues == NESet(AxisPoint("mode" -> "test"))
      case _ => false
    })
  }

}
