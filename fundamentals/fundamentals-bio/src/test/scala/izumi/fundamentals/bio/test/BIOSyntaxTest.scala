package izumi.fundamentals.bio.test

import izumi.functional.bio.{BIO, BIOAsk, BIOFork, BIOFork3, BIOFunctor, BIOLocal, BIOMonad, BIOMonad3, BIOMonadError, BIOPrimitives, BIOPrimitives3, BIOTemporal, F}
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.duration._

class BIOSyntaxTest extends AnyWordSpec {

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

  "BIO.when/unless/ifThenElse have nice inference" in {
    def x[F[+_, +_]: BIOMonad] = {
      F.ifThenElse(F.pure(false): F[RuntimeException, Boolean])(F.pure(()), F.pure(()): F[Throwable, Any]) *>
      F.when(F.pure(false): F[RuntimeException, Boolean])(F.pure(()): F[Throwable, Unit])
    }

    x[zio.IO]
  }

  "withFilter test" in {
    def x[F[+_, +_]: BIOMonadError]: F[NoSuchElementException, Unit] = {
      assertDoesNotCompile(
        """
        for {
          (1, 2) <- F.pure(Option((2, 1)))
        } yield ()
        """
      )
      for {
        (1, 2) <- F.pure((2, 1))
      } yield ()
    }
    x[zio.IO]
  }

  "F / FR summoners examples" in {
    def x[F[+_, +_]: BIOMonad] = {
      F.when(false)(F.unit)
    }
    def y[F[+_, +_]: BIOTemporal] = {
      F.timeout(F.forever(F.unit))(5.seconds) *>
      F.map(z[F])(_ => ())
    }
    def z[F[+_, +_]: BIOFunctor]: F[Nothing, Unit] = {
      F.map(z[F])(_ => ())
    }
    def `attach BIOPrimitives & BIOFork methods even when they aren't imported`[F[+_, +_]: BIOMonad: BIOPrimitives: BIOFork]: F[Nothing, Int] = {
      F.fork[Any, Nothing, Int] {
          F.mkRef(4).flatMap(r => r.update(_ + 5) *> r.get.map(_ - 1))
        }.flatMap(_.join) *>
      F.mkRef(4).flatMap(r => r.update(_ + 5) *> r.get.map(_ - 1)).fork.flatMap(_.join)
    }
    def `attach BIOPrimitives & BIOFork3 methods to a trifunctor BIO even when not imported`[FR[-_, +_, +_]: BIOMonad3: BIOPrimitives3: BIOFork3]
      : FR[Nothing, Nothing, Int] = {
      F.fork(F.mkRef(4).flatMap(r => r.update(_ + 5) *> r.get.map(_ - 1))).flatMap(_.join) *>
      F.mkRef(4).flatMap(r => r.update(_ + 5) *> r.get.map(_ - 1)).fork.flatMap(_.join)
    }
    lazy val _ = (
      x[zio.IO],
      y[zio.IO](_: BIOTemporal[zio.IO]),
      z[zio.IO],
      `attach BIOPrimitives & BIOFork methods even when they aren't imported`[zio.IO],
      `attach BIOPrimitives & BIOFork3 methods to a trifunctor BIO even when not imported`[zio.ZIO],
    )
  }

  "FR: Local/Ask summoners examples" in {
    def x[FR[-_, +_, +_]: BIOMonad3: BIOAsk] = {
      F.unit *> F.access {
        _: Int =>
          ()
      }
    }
    def y[FR[-_, +_, +_]: BIOMonad3: BIOLocal]: FR[Any, Throwable, Unit] = {
      F.fromKleisli {
        F.accessThrowable {
          _: Int =>
            ()
        }.toKleisli
      }.provide(4)
    }
    lazy val _ = (
      x[zio.ZIO],
      y[zio.ZIO],
    )
  }
}
