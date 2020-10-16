package izumi.functional.bio.test

import cats.effect.laws.ConcurrentLaws
import cats.effect.laws.discipline.ConcurrentTests
import cats.effect.{Concurrent, ContextShift}
import izumi.functional.bio.catz.BIOAsyncForkToConcurrent
import izumi.functional.bio.env.MonixEnv

class BIOMonixLawsTest extends CatsLawsTestBase with MonixEnv {
  val concurrentTestsMonix = new ConcurrentTests[monix.bio.Task] {
    override val laws = new ConcurrentLaws[monix.bio.Task] {
      val F = Concurrent[monix.bio.Task](BIOAsyncForkToConcurrent)
      val contextShift = ContextShift[monix.bio.Task](monix.bio.IO.contextShift)
    }
  }

  checkAll("ConcurrentMonix", concurrentTestsMonix.sync[Int, Int, Int])
}
