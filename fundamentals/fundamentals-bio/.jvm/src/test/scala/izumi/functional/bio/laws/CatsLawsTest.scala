package izumi.functional.bio.laws

import cats.effect.kernel.Async
import cats.effect.laws.AsyncTests
import izumi.functional.bio.catz
import izumi.functional.bio.impl.CatsToBIO
import izumi.functional.bio.impl.CatsToBIO.Bifunctorized
import izumi.functional.bio.laws.env.CatsTestEnv

class CatsLawsTest extends CatsLawsTestBase with CatsTestEnv {

  checkAll(
    "AsyncCE", {
      implicit val ticker: Ticker = Ticker()
      implicit val BIO = CatsToBIO.asyncToBIO[cats.effect.IO]
      implicit val CE: Async[Bifunctorized[cats.effect.IO, Throwable, +_]] = catz.BIOToAsync
      import scala.concurrent.duration.DurationInt
      AsyncTests[Bifunctorized[cats.effect.IO, Throwable, +_]].async[Int, Int, Int](5.second)
    },
  )

}
