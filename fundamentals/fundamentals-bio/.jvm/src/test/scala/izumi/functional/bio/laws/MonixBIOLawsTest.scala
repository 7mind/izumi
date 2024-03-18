//package izumi.functional.bio.laws
//
//import cats.effect.kernel.ConcurrentEffect
//import cats.effect.laws.ConcurrentEffectLaws
//import cats.effect.laws.discipline.ConcurrentEffectTests
//import izumi.functional.bio.{UnsafeRun2, catz}
//import izumi.functional.bio.laws.env.MonixTestEnv
//import monix.execution.schedulers.TestScheduler
//
//class MonixBIOLawsTest extends CatsLawsTestBase with MonixTestEnv {
//  implicit val testScheduler: TestScheduler = TestScheduler()
//  implicit val runtime: UnsafeRun2[monix.bio.IO] = UnsafeRun2.createMonixBIO(testScheduler, opt)
//  implicit val CE: ConcurrentEffect[monix.bio.Task] = ConcurrentEffect[monix.bio.Task](catz.BIOAsyncForkUnsafeRunToConcurrentEffect)
//
//  val concurrentEffectTestsMonix: ConcurrentEffectTests[monix.bio.Task] = new ConcurrentEffectTests[monix.bio.Task] {
//    override val laws = new ConcurrentEffectLaws[monix.bio.Task] {
////      override val F = CE
////      override val contextShift = cs
//      override val F = implicitly
//      override val contextShift = implicitly
//    }
//  }
//
//  checkAll("ConcurrentEffectMonix", concurrentEffectTestsMonix.concurrentEffect[Int, Int, Int])
//}
