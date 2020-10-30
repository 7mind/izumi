package izumi.functional.bio.laws

import cats.effect.laws.ConcurrentEffectLaws
import cats.effect.laws.discipline.{ConcurrentEffectTests, Parameters}
import cats.effect.laws.util.TestContext
import cats.effect.{ConcurrentEffect, ContextShift}
import izumi.functional.bio.{UnsafeRun2, catz}
import izumi.functional.bio.laws.env.{ZIOTestEnvNonterminating, ZIOTestEnvTerminating}
import zio.IO
import zio.internal.{Executor, Platform, Tracing}

class ZIOLawsTest extends CatsLawsTestBase with ZIOTestEnvNonterminating {
  implicit val testContext: TestContext = TestContext()
  val platform = Platform
    .fromExecutor(Executor.fromExecutionContext(Int.MaxValue)(testContext))
    .withTracing(Tracing.disabled)
    .withReportFailure(_ => ())
  implicit val unsafeRun: UnsafeRun2[IO] = UnsafeRun2.createZIO(platform)
  val cs: ContextShift[zio.Task] = zio.interop.catz.zioContextShift
  implicit val CE: ConcurrentEffect[zio.Task] = catz.BIOAsyncForkUnsafeRunToConcurrentEffect
//  implicit val CE: ConcurrentEffect[zio.Task] = zio.interop.catz.taskEffectInstance(zio.Runtime((), platform))

  lazy val concurrentEffectTestZio: ConcurrentEffectTests[zio.Task] = new ConcurrentEffectTests[zio.Task] {
    override val laws = new ConcurrentEffectLaws[zio.Task] {
      override val F = CE
      override val contextShift = cs
    }
  }

  implicit def params: Parameters =
    Parameters(stackSafeIterationsCount = 10000, allowNonTerminationLaws = true)

//  checkAll("ConcurrentZIO", concurrentEffectTestZio.concurrent[Int, Int, Int])
  checkAll("ConcurrentEffectZIO", concurrentEffectTestZio.concurrentEffect[Int, Int, Int])
}
