package izumi.functional.bio.laws

import cats.effect.kernel.Async
import cats.effect.laws.AsyncTests
import izumi.functional.bio.{Bifunctorized, catz}
import izumi.functional.bio.impl.CatsToBIO
import izumi.functional.bio.laws.env.CatsTestEnv

class CatsLawsTest extends CatsLawsTestBase with CatsTestEnv {

  checkAll(
    "AsyncCE", {
      implicit val ticker: Ticker = Ticker()
      // `TagK[cats.effect.IO]` is macro-derived at the `CatsToBIO.asyncToBIO[cats.effect.IO]`
      // call site (the factory takes `(implicit F: Async[F], tag: TagK[F])`). No explicit
      // `implicit val tagK` is needed — and declaring one as `TagK[X] = TagK[X]` would create
      // a forward-reference / infinite-loop on Scala 3.
      implicit val BIO: izumi.functional.bio.Async2[Bifunctorized[cats.effect.IO, +_, +_]] &
        izumi.functional.bio.Temporal2[Bifunctorized[cats.effect.IO, +_, +_]] &
        izumi.functional.bio.Fork2[Bifunctorized[cats.effect.IO, +_, +_]] &
        izumi.functional.bio.BlockingIO2[Bifunctorized[cats.effect.IO, +_, +_]] &
        izumi.functional.bio.Primitives2[Bifunctorized[cats.effect.IO, +_, +_]] &
        izumi.functional.bio.Clock2[Bifunctorized[cats.effect.IO, +_, +_]] = CatsToBIO.asyncToBIO[cats.effect.IO]
      implicit val CE: Async[Bifunctorized[cats.effect.IO, Throwable, +_]] = catz.BIOToAsync
      import scala.concurrent.duration.DurationInt
      AsyncTests[Bifunctorized[cats.effect.IO, Throwable, +_]].async[Int, Int, Int](5.second)
    },
  )

}
