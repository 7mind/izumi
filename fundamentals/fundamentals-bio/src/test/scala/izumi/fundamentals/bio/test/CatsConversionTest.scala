package izumi.fundamentals.bio.test

import cats.effect.concurrent.Ref
import cats.effect.{Async, Concurrent, Sync}
import izumi.functional.bio.BIO
import izumi.functional.bio.catz._
import org.scalatest.WordSpec

class CatsConversionTest extends WordSpec {

  class X[F[+_, +_]: BIO](val ref: Ref[F[Throwable, ?], Int])

  "pickup conversion to Sync" in {
    def conv[F[+_, +_]: BIO]: F[Throwable, Int] = {
      Ref.of(0)
        .flatMap(_.get)
    }
    conv[zio.IO]
    implicitly[Sync[zio.Task]]
    implicitly[Async[zio.Task]]
    implicitly[Concurrent[zio.Task]]
  }

  "pickup conversion to Monad" in {
    import cats.syntax.applicative._
    import cats.syntax.monad._

    def conv[F[+_, +_]: BIO]: F[Nothing, Unit] = {
      ().iterateWhileM(_ => ().pure)(_ => true)
    }
  }

}
