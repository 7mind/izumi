package izumi.fundamentals.bio.test

import cats.effect.concurrent.Ref
import cats.effect.{Async, Concurrent, Sync}
import izumi.functional.bio.{BIO, F}
import izumi.functional.bio.catz._
import org.scalatest.wordspec.AnyWordSpec
import izumi.functional.bio.BIOAsync
import cats.Parallel

class CatsConversionTest extends AnyWordSpec {

  class X[F[+_, +_]: BIO](val ref: Ref[F[Throwable, ?], Int])

  "pickup conversion to Sync" in {
    def conv[F[+_, +_]: BIO]: F[Throwable, Int] = {
      Ref.of(0)
        .flatMap(_.get)
    }
    conv[zio.IO]
    implicitly[Sync[zio.Task]]
    implicitly[Async[zio.Task]]
    implicitly[Parallel[zio.Task]]
    implicitly[Concurrent[zio.Task]]
  }

  "pickup conversion to Monad" in {
    def c1[F[+_, +_]: BIO]: F[Nothing, Unit] = {
      import cats.syntax.applicative._
      import cats.syntax.monad._

      ().iterateWhileM(_ => ().pure)(_ => true)
    }
    def c2[F[+_, +_]: BIO]: F[Nothing, List[Unit]] = {
      import cats.implicits._

      List(1, 2, 3).traverseFilter {
        case 2 => F.pure(Some(()))
        case _ => F.pure(None)
      }
    }

    c1[zio.IO]
    c2[zio.IO]
  }
}
