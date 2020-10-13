package izumi.distage.testkit.distagesuite.compiletime

import izumi.distage.framework.{PerformPlanCheck, PlanCheck}
import izumi.distage.roles.test.TestEntrypointPatchedLeak
import org.scalatest.exceptions.TestFailedException
import org.scalatest.wordspec.AnyWordSpec

final class CompileTimePlanCheckerTest extends AnyWordSpec {

  "check role app module" in {
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
