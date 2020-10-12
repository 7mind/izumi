package izumi.distage.testkit.distagesuite.compiletime

import izumi.distage.framework.{PlanCheck, PlanCheckMacro}
import izumi.distage.roles.test.TestEntrypoint
import org.scalatest.wordspec.AnyWordSpec

final class CompTimePlanCheckerTest extends AnyWordSpec {

  "check role app module" in {
    PlanCheck.checkRoleApp(TestEntrypoint, "configwriter help")

//    PlanCheck.checkRoleApp(
//      TestEntrypoint,
//      "testtask00 testrole01 testrole02 testrole03 testrole04",
//      "mode:prod axiscomponentaxis:correct | mode:prod axiscomponentaxis:incorrect",
//    )

    new PlanCheckMacro.Impl(
      TestEntrypoint,
      "testtask00 testrole01 testrole02 testrole03 testrole04",
      activations = "mode:prod axiscomponentaxis:correct | mode:prod axiscomponentaxis:incorrect",
    ).planCheck.run()

    new PlanCheckMacro.Impl(
      TestEntrypoint,
      activations = "mode:prod axiscomponentaxis:correct | mode:prod axiscomponentaxis:incorrect",
      checkConfig = false,
    ).planCheck.run()
//
//
//    assertThrows[Throwable] {
//      new PlanCheckMacro.Impl(
//        TestEntrypoint,
//        config = "testrole04-reference.conf",
//        activations = "mode:prod axiscomponentaxis:correct | mode:prod axiscomponentaxis:incorrect",
//      ).planCheck.run()
//    }

//    new PlanCheckMacro.Impl(
//      TestEntrypoint,
//      config = "checker-test-good.conf",
//      activations = "mode:prod axiscomponentaxis:correct | mode:prod axiscomponentaxis:incorrect",
//    ).planCheck.run()

//      new PlanCheckMacro.Impl(
//        TestEntrypoint,
//        "testtask00 testrole01 testrole02 testrole03 testrole04",
//      ).planCheck.run()

//      PlanCheck.checkRoleApp(
//        TestEntrypoint,
//        "testtask00 testrole01 testrole02 testrole03 testrole04",
//        "mode:test axiscomponentaxis:correct | mode:test axiscomponentaxis:incorrect",
//      )
  }

}
