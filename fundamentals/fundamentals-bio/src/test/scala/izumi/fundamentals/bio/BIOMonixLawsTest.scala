package izumi.fundamentals.bio

import cats.effect.laws.ConcurrentLaws
import cats.effect.laws.discipline.ConcurrentTests
import cats.effect.{Concurrent, ContextShift}
import izumi.functional.bio.catz._
import izumi.fundamentals.bio.env.MonixEnv

class BIOMonixLawsTest extends CatsLawsTestBase with MonixEnv {
    val concurrentTestsMonix = new ConcurrentTests[monix.bio.Task] {
      override val laws = new ConcurrentLaws[monix.bio.Task] {
        val F = Concurrent[monix.bio.Task]
        val contextShift = ContextShift[monix.bio.Task]
      }
    }

  checkAll("ConcurrentMonix", concurrentTestsMonix.sync[Int, Int, Int])
}
