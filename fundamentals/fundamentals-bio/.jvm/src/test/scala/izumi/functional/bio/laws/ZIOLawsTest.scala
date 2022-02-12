//package izumi.functional.bio.laws
//
//import cats.effect.kernel.Temporal
//import izumi.functional.bio.laws.env.{ZIOTestEnvNonterminating, ZIOTestEnvTerminating}
//import izumi.functional.bio.{UnsafeRun2, catz}
//import zio.IO
//import zio.internal.{Executor, Platform, Tracing}
//
//class ZIOLawsTestTerminating extends CatsLawsTestBase with ZIOTestEnvTerminating {
//
//  implicit val unsafeRun: UnsafeRun2[IO] = UnsafeRun2.createZIO(Platform.default.withTracing(Tracing.disabled).withReportFailure(_ => ()))
//  implicit val CE: Temporal[zio.Task] = catz.BIOAsyncForkUnsafeRunToConcurrentEffect
//
//  implicit val params: Parameters = Parameters.default.copy(
//    allowNonTerminationLaws = false
//  )
//
//  lazy val concurrentEffectTestZio: ConcurrentEffectTests[zio.Task] = new ConcurrentEffectTests[zio.Task] {
//    override val laws = new ConcurrentEffectLaws[zio.Task] {
//      override val F = CE
//      override val contextShift = cs
//    }
//  }
//
//  checkAll("ConcurrentEffectZIO", concurrentEffectTestZio.concurrentEffect[Int, Int, Int])
//}
//
//class ZIOLawsTestNonterminating extends CatsLawsTestBase with ZIOTestEnvNonterminating {
//  implicit val testContext: TestContext = TestContext()
//  val platform: Platform = Platform
//    .fromExecutor(Executor.fromExecutionContext(Int.MaxValue)(testContext))
//    .withTracing(Tracing.disabled)
//    .withReportFailure(_ => ())
//
//  val cs: ContextShift[zio.Task] = zio.interop.catz.zioContextShift
//  implicit val unsafeRun: UnsafeRun2[IO] = UnsafeRun2.createZIO(platform)
//  implicit val CE: ConcurrentEffect[zio.Task] = catz.BIOAsyncForkUnsafeRunToConcurrentEffect
//
//  implicit val params: Parameters = Parameters.default.copy(
//    allowNonTerminationLaws = false
//  )
//
//  lazy val concurrentEffectTestZio: ConcurrentEffectTests[zio.Task] = new ConcurrentEffectTests[zio.Task] {
//    override val laws = new ConcurrentEffectLaws[zio.Task] {
//      override val F = CE
//      override val contextShift = cs
//    }
//  }
//
//  checkAll("ConcurrentEffectZIO", concurrentEffectTestZio.concurrentEffect[Int, Int, Int])
//}
