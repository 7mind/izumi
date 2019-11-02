package izumi.fundamentals.bio

import izumi.functional.bio.{BIO, BIOAsync, BIOFunctor, BIOMonad, F}
import org.scalatest.WordSpec

import scala.concurrent.duration._

class BIOSyntaxTest extends WordSpec {

  "BIO.apply is callable" in {
    class X[F[+_, +_]: BIO] {
      def hello = BIO(println("hello world!"))
    }

    assert(new X[zio.IO].hello != null)
  }

  "BIO.widen/widenError is callable" in {
    def x[F[+_, +_]: BIO]: F[Throwable, AnyVal] = {
      identity[F[Throwable, AnyVal]] {
        BIO[F].pure(None: Option[Int]).flatMap {
          _.fold(
            BIO[F].unit.widenError[Throwable].widen[AnyVal]
          )(
            _ => BIO[F].fail(new RuntimeException)
          )
        }
      }
    }

    x[zio.IO]
  }

  "F summoner examples" in {
    def x[F[+_, +_]: BIOMonad] = {
      F.when(false)(F.unit)
    }
    def y[F[+_, +_]: BIOAsync] = {
      F.timeout(F.forever(F.unit))(5.seconds)
    }
    def z[F[+_, +_]: BIOFunctor]: F[Nothing, Unit] = {
      F.map(z[F])(_ => ())
    }
    lazy val a = x
    lazy val b = y[zio.IO](_: BIOAsync[zio.IO])
    lazy val c = z
    lazy val _ = (a, b, c)
  }

}
