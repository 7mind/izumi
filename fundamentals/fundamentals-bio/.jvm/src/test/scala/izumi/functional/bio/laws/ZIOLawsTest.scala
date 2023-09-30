package izumi.functional.bio.laws

import cats.effect.kernel.Async
import cats.effect.laws.AsyncTests
import izumi.functional.bio.catz
import izumi.functional.bio.laws.env.ZIOTestEnv

class ZIOLawsTest extends CatsLawsTestBase with ZIOTestEnv {

  checkAll(
    "AsyncZIO", {
      implicit val ticker: Ticker = Ticker()
      implicit val CE: Async[zio.Task] = catz.BIOToAsync
      import scala.concurrent.duration.DurationInt
      AsyncTests[zio.Task].async[Int, Int, Int](5.second)
    },
  )

}
