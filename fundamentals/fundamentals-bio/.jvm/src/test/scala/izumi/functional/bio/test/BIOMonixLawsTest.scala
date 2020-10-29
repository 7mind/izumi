package izumi.functional.bio.test

import cats.effect.ConcurrentEffect
import cats.effect.laws.ConcurrentEffectLaws
import cats.effect.laws.discipline.ConcurrentEffectTests
import izumi.functional.bio.env.MonixEnv
import izumi.functional.bio.{UnsafeRun2, catz}
import monix.execution.schedulers.TestScheduler

class BIOMonixLawsTest extends CatsLawsTestBase with MonixEnv {
  implicit val testScheduler: TestScheduler = TestScheduler()
  implicit val runtime: UnsafeRun2[monix.bio.IO] = UnsafeRun2.createMonixBIO(testScheduler, opt)
  implicit val CE: ConcurrentEffect[monix.bio.Task] = ConcurrentEffect[monix.bio.Task](catz.BIOAsyncForkUnsafeRunToConcurrentEffect)

  val concurrentEffectTestsMonix: ConcurrentEffectTests[monix.bio.Task] = new ConcurrentEffectTests[monix.bio.Task] {
    override val laws = new ConcurrentEffectLaws[monix.bio.Task] {
      override val F = CE
      override val contextShift = cs
    }
  }

  checkAll("SyncMonix", concurrentEffectTestsMonix.sync[Int, Int, Int])
//  checkAll("ConcurrentMonix", concurrentEffectTestsMonix.concurrent[Int, Int, Int])
//  checkAll("ConcurrentEffectMonix", concurrentEffectTestsMonix.concurrentEffect[Int, Int, Int])
}
