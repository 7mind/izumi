package izumi.distage.testkit.distagesuite.compiletime

import com.github.pshirshov.test.plugins.{StaticTestMain, StaticTestMainBadEffect, StaticTestMainLogIO2}
import com.github.pshirshov.test2.plugins.Fixture2
import com.github.pshirshov.test2.plugins.Fixture2.{Dep, MissingDep}
import com.github.pshirshov.test3.bootstrap.BootstrapFixture3.{BootstrapComponent, UnsatisfiedDep}
import com.github.pshirshov.test3.plugins.Fixture3
import com.github.pshirshov.test4.Fixture4
import izumi.distage.framework.model.exceptions.PlanCheckException
import izumi.distage.framework.{PlanCheck, PlanCheckConfig}
import izumi.distage.model.planning.PlanIssue
import izumi.distage.model.reflection.DIKey
import izumi.distage.roles.test.CustomCheckEntrypoint
import izumi.fundamentals.platform.IzPlatform
import logstage.LogIO2
import org.scalatest.exceptions.TestFailedException
import org.scalatest.wordspec.AnyWordSpec

final class CompileTimePlanCheckerTest extends AnyWordSpec {

  "Check without config" in {
    PlanCheck.assertAppCompileTime(StaticTestMain, PlanCheckConfig("statictestrole", checkConfig = false)).assertAgainAtRuntime()
    PlanCheck.assertAppCompileTime(StaticTestMain, PlanCheckConfig("statictestrole", excludeActivations = "test:y", checkConfig = false)).assertAgainAtRuntime()
  }

  "Check with invalid role produces error" in {
    val result = PlanCheck.runtime.checkApp(StaticTestMain, PlanCheckConfig("unknownrole"))
    assert(result.maybeErrorMessage.exists(_.contains("Unknown roles:")))
    assert(result.issues.fromNESet.isEmpty)

    val err = intercept[TestFailedException](assertCompiles("""
      PlanCheck.assertAppCompileTime(StaticTestMain, PlanCheckConfig("unknownrole"))
      """.stripMargin))
    assert(err.getMessage.contains("Unknown roles:"))
  }

  "onlyWarn mode does not fail compilation on errors" in {
    assertThrows[PlanCheckException] {
      PlanCheck.runtime.assertApp(StaticTestMain, PlanCheckConfig("statictestrole", config = "check-test-bad.conf"))
    }
    assert(
      intercept[TestFailedException](
        assertCompiles(
          """
          PlanCheck.assertAppCompileTime(StaticTestMain, PlanCheckConfig("statictestrole", config = "check-test-bad.conf", onlyWarn = false))
          """
        )
      ).getMessage contains "cannot parse configuration"
    )
    assertCompiles(
      """
      PlanCheck.assertAppCompileTime(StaticTestMain, PlanCheckConfig("statictestrole", config = "check-test-bad.conf", onlyWarn = true))
      """
    )
  }

  "Do not report errors for parts of the graph only accessible via excluded activations" in {
    PlanCheck.assertAppCompileTime(Fixture2.TestRoleAppMain, PlanCheckConfig(excludeActivations = "mode:test"))
    PlanCheck.runtime.assertApp(Fixture2.TestRoleAppMain, PlanCheckConfig(excludeActivations = "mode:test"))

    // fail without exclusion
    assert(
      intercept[TestFailedException](
        assertCompiles(
          """
          PlanCheck.assertAppCompileTime(Fixture2.TestRoleAppMain)
          """
        )
      ).getMessage contains "Found a problem with your DI wiring"
    )
    val err = intercept[PlanCheckException] {
      PlanCheck.runtime.assertApp(Fixture2.TestRoleAppMain)
    }
    assert(err.cause.isRight)
    assert(err.issues.fromNESet.forall(_.isInstanceOf[PlanIssue.MissingImport]))
    assert(err.issues.fromNESet.head.asInstanceOf[PlanIssue.MissingImport].key == DIKey[MissingDep])
    assert(err.issues.fromNESet.head.asInstanceOf[PlanIssue.MissingImport].dependee == DIKey[Dep])
  }

  "Check bindings in bootstrap plugins (bootstrap bindings are always deemed roots)" in {
    val err = intercept[TestFailedException](
      assertCompiles(
        """
          |PlanCheck.assertAppCompileTime(Fixture3.TestRoleAppMainFailing)
          |""".stripMargin
      )
    )
    assert(err.getMessage contains "Instance is not available")
    assert(err.getMessage contains "UnsatisfiedDep")
    assert(err.getMessage contains "BootstrapComponent")

    val res = PlanCheck.runtime.checkApp(Fixture3.TestRoleAppMainFailing)
    if (IzPlatform.isScalaJS) {
      assert(res.issues.fromNESet.size == 2) // Scala.js plan checker does not support config checks yet
    } else {
      assert(res.issues.fromNESet.size == 1)
    }
    assert(res.issues.fromNESet.head.asInstanceOf[PlanIssue.MissingImport].key == DIKey[UnsatisfiedDep])
    // BootstrapComponent is a root, despite not being reachable from role roots because it's defined in a Bootstrap Plugin
    assert(res.issues.fromNESet.head.asInstanceOf[PlanIssue.MissingImport].dependee == DIKey[BootstrapComponent])
  }

  "report error on invalid effect type" in {
    val result = PlanCheck.runtime.checkApp(StaticTestMainBadEffect, PlanCheckConfig("statictestrole", checkConfig = false))
    assert(result.issues.fromNESet.map(_.getClass) == Set(classOf[PlanIssue.IncompatibleEffectType]))

    val err = intercept[TestFailedException](assertCompiles("""
      PlanCheck.assertAppCompileTime(StaticTestMainBadEffect, PlanCheckConfig("statictestrole", checkConfig = false)).assertAgainAtRuntime()
      """))

    assert(err.getMessage.contains("injector uses effect λ %0 → 0 but binding uses incompatible effect λ %0 → cats.effect.IO[+0]"))
  }

  "StaticTestMainLogIO2 check passes with a LogIO2 dependency" in {
    val res = PlanCheck.runtime.checkApp(new StaticTestMainLogIO2[zio.IO], PlanCheckConfig(checkConfig = false))
    assert(res.visitedKeys contains DIKey[LogIO2[zio.IO]])
  }

  "check subcontext submodule fails for missing bindings" in {
    val Some(issues) = PlanCheck.runtime.checkApp(Fixture4.TestMainBad).issues: @unchecked
    assert(issues.size == 1)
    assert(issues.forall(_.isInstanceOf[PlanIssue.MissingImport]))
    assert(issues.forall(_.asInstanceOf[PlanIssue.MissingImport].key == DIKey[Fixture4.MissingDep]))
  }

  "check passes for subcontext submodule if missing binding is marked as a local dependency" in {
    new PlanCheck.Main(Fixture4.TestMainGood)
      .assertAgainAtRuntime()
    val (loc, close) = Fixture4.TestMainGood.replLocatorWithClose(":target")
    val dep = loc.get[Fixture4.TargetRole].mkDep()
    close()
    assert(dep != null)
  }

  "Support custom checks" in {
    val res = PlanCheck.runtime.checkApp(
      CustomCheckEntrypoint,
      PlanCheckConfig(
        roles = "* -failingrole01 -failingrole02",
        checkConfig = false,
        excludeActivations = "mode:test",
      ),
    )
    assert(res.maybeErrorMessage.exists(_.contains("Custom check failed")))

    val err = intercept[TestFailedException](assertCompiles("""
      new PlanCheck.Main(
        CustomCheckEntrypoint,
        PlanCheckConfig(
          roles = "* -failingrole01 -failingrole02",
          checkConfig = false,
          excludeActivations = "mode:test",
        ),
      ).assertAgainAtRuntime()
    """))
    assert(err.getMessage.contains("Custom check failed"))
  }

}
