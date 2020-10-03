package izumi.functional.bio.test

import cats.effect.laws.ConcurrentLaws
import cats.effect.laws.discipline.ConcurrentTests
import cats.effect.{Concurrent, ContextShift}
import izumi.functional.bio.catz.BIOAsyncForkToConcurrent
import izumi.functional.bio.env.ZIOTestEnv

class BioZioLawsTest extends CatsLawsTestBase with ZIOTestEnv {
  val concurrentTestZio = new ConcurrentTests[zio.Task] {
    val laws = new ConcurrentLaws[zio.Task] {
      val F = Concurrent[zio.Task](BIOAsyncForkToConcurrent)
      val contextShift = ContextShift[zio.Task](zio.interop.catz.zioContextShift)
    }
  }

  checkAll("ConcurrentZIO", concurrentTestZio.sync[Int, Int, Int])
}
