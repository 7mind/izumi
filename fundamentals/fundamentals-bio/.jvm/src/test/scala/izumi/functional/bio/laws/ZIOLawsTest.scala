package izumi.functional.bio.laws

import cats.effect.laws.ConcurrentEffectLaws
import cats.effect.laws.discipline.ConcurrentEffectTests
import cats.effect.laws.util.TestContext
import cats.effect.{ConcurrentEffect, ContextShift}
import izumi.functional.bio.laws.env.ZIOTestEnv
import izumi.functional.bio.{UnsafeRun2, catz}
import zio.IO
import zio.internal.{Executor, Platform, Tracing}

class ZIOLawsTest extends CatsLawsTestBase with ZIOTestEnv {
  implicit val testContext: TestContext = TestContext()
  implicit val runtime: UnsafeRun2[IO] = UnsafeRun2.createZIO {
    Platform
      .fromExecutor(Executor.fromExecutionContext(Int.MaxValue)(testContext))
      .withTracing(Tracing.disabled)
      .withReportFailure(_ => ())
  }
  implicit val CE: ConcurrentEffect[zio.Task] = ConcurrentEffect[zio.Task](catz.BIOAsyncForkUnsafeRunToConcurrentEffect)

  val concurrentEffectTestZio: ConcurrentEffectTests[zio.Task] = new ConcurrentEffectTests[zio.Task] {
    override val laws = new ConcurrentEffectLaws[zio.Task] {
      override val F = CE
      override val contextShift = ContextShift[zio.Task](zio.interop.catz.zioContextShift)
    }
  }

  checkAll("ConcurrentZIO", concurrentEffectTestZio.concurrent[Int, Int, Int])
//  checkAll("ConcurrentEffectZIO", concurrentEffectTestZio.concurrentEffect[Int, Int, Int])
}
