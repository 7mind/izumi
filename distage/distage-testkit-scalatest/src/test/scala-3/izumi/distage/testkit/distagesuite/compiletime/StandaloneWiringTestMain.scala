//package izumi.distage.testkit.distagesuite.compiletime
//
//import izumi.distage.framework.{PlanCheck, PlanCheckConfig}
//import izumi.distage.roles.test.TestEntrypointPatchedLeak
//
//object StandaloneWiringTestMain
//  extends PlanCheck.Main(
//    TestEntrypointPatchedLeak,
//    PlanCheckConfig(
//      "* -failingrole01 -failingrole02",
//      "mode:test",
//    ),
//  )
//
//object StandaloneWiringTestMain2
//  extends TestEntrypointPatchedLeak.PlanCheck(
//    PlanCheckConfig(
//      "* -failingrole01 -failingrole02",
//      "mode:test",
//    )
//  )
