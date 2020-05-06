package izumi.fundamentals.bio.test

import izumi.functional.bio.{BIO, BIOArrow, BIOAsk, BIOBifunctor3, BIOFork, BIOFork3, BIOFunctor, BIOLocal, BIOMonad, BIOMonad3, BIOMonadAsk, BIOMonadError, BIOParallel, BIOPrimitives, BIOPrimitives3, BIOProfunctor, BIOTemporal, BIOTemporal3, F}
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.duration._

class BIOSyntaxTest extends AnyWordSpec {

  "BIOParallel.zipPar/zipParLeft/zipParRight/zipWithPar is callable" in {
    def x[F[+_, +_]: BIOParallel](a: F[Nothing, Unit], b: F[Nothing, Unit]) = {
      a zipPar b
      a zipParLeft b
      a zipParRight b
      a.zipWithPar(b)((a, b) => (a, b))
    }

    x[zio.IO](zio.UIO.succeed(()), zio.UIO.succeed(()))
  }

  "BIOParallel3.zipPar/zipParLeft/zipParRight/zipWithPar is callable" in {
    import izumi.functional.bio.BIOParallel3
    def x[F[-_, +_, +_]: BIOParallel3](a: F[Any, Nothing, Unit], b: F[Any, Nothing, Unit]) = {
      a zipPar b
      a zipParLeft b
      a zipParRight b
      a.zipWithPar(b)((a, b) => (a, b))
    }

    x[zio.ZIO](zio.UIO.succeed(()), zio.UIO.succeed(()))
  }

  "BIOAsync.race is callable along with all BIOParallel syntax" in {
    import izumi.functional.bio.BIOAsync
    def x[F[+_, +_]: BIOAsync](a: F[Nothing, Unit], b: F[Nothing, Unit]) = {
      a zipPar b
      a zipParLeft b
      a zipParRight b
      a.zipWithPar(b)((a, b) => (a, b))
      a race b
    }

    x[zio.IO](zio.UIO.succeed(()), zio.UIO.succeed(()))
  }

  "BIOAsync3.race is callable" in {
    import izumi.functional.bio.BIOAsync3

    def x[F[-_, +_, +_]: BIOAsync3](a: F[Any, Nothing, Unit], b: F[Any, Nothing, Unit]) = {
      a zipPar b
      a zipParLeft b
      a zipParRight b
      a.zipWithPar(b)((a, b) => (a, b))
      a race b
    }

    x[zio.ZIO](zio.UIO.succeed(()), zio.UIO.succeed(()))
  }

  "BIO.apply is callable" in {
    class X[F[+_, +_]: BIO] {
      def hello = BIO(println("hello world!"))
    }

    assert(new X[zio.IO].hello != null)
  }

  "BIO.widen/widenError is callable" in {
    def x[F[+_, +_]: BIO]: F[Throwable, AnyVal] = {
      identity[F[Throwable, AnyVal]] {
        F.pure(None: Option[Int]).flatMap {
          _.fold(
            F.unit.widenError[Throwable].widen[AnyVal]
          )(
            _ => F.fail(new RuntimeException)
          )
        }
      }
    }

    x[zio.IO]
  }

  "BIOBracket.bracketCase & guaranteeCase are callable" in {
    import izumi.functional.bio.BIOBracket
    import izumi.functional.bio.BIOExit

    def x[F[+_, +_]: BIOBracket]: F[Throwable, Int] = {
      F.pure(None).bracketCase(release = {
          (_, _: BIOExit[Throwable, Int]) => F.unit
        })(_ => F.pure(1))
    }
    def y[F[+_, +_]: BIOBracket]: F[Throwable, Int] = {
      F.pure(None).bracketCase[Throwable, Int] {
          case (_, BIOExit.Success(x)) => F.pure(x).as(())
          case (_, _) => F.unit
        }(_ => F.pure(1))
    }
    def z[F[+_, +_]: BIOBracket]: F[Throwable, Int] = {
      F.pure(1).guaranteeCase {
        case BIOExit.Success(x) => F.pure(x).as(())
        case _ => F.unit
      }
    }
    def zz[F[+_, +_]: BIOBracket]: F[Throwable, Int] = {
      F.when(F.pure(false).widenError[Throwable])(F.unit).as(1).guaranteeCase {
          case BIOExit.Success(x) => F.pure(x).as(())
          case _ => F.unit
        }.widenError[Throwable]
    }

    x[zio.IO]
    y[zio.IO]
    z[zio.IO]
    zz[zio.IO]
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
    def `attach BIOPrimitives & BIOFork3 methods to a trifunctor BIO even when not imported`[
      FR[-_, +_, +_]: BIOMonad3: BIOPrimitives3: BIOFork3
    ]: FR[Nothing, Nothing, Int] = {
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

  "Support BIO syntax for ZIO with wildcard import" in {
    import izumi.functional.bio._
    zio.IO.effectTotal(List(4)).flatMap {
      F.traverse(_)(_ => zio.IO.unit)
    } *> F.unit
    implicitly[BIOBifunctor3[zio.ZIO]]
    implicitly[BIOBifunctor[zio.IO]]
  }

  "FR: Local/Ask summoners examples" in {
    def x[FR[-_, +_, +_]: BIOMonad3: BIOAsk] = {
      F.unit *> F.ask[Int].map {
        _: Int =>
          true
      } *>
      F.unit *> F.askWith {
        _: Int =>
          true
      }
    }
    def onlyMonadAsk[FR[-_, +_, +_]: BIOMonadAsk]: FR[Int, Nothing, Unit] = {
      F.unit <* F.askWith {
        _: Int =>
          true
      }
    }
    def onlyMonadAskAccess[FR[-_, +_, +_]: BIOMonadAsk]: FR[Int, Nothing, Unit] = {
      F.unit <* F.access {
        _: Int =>
          F.unit
      }
    }
    def onlyAsk[FR[-_, +_, +_]: BIOAsk]: FR[Int, Nothing, Unit] = {
      F.askWith {
        _: Int =>
          true
      } *> F.unit
    }
    def y[FR[-_, +_, +_]: BIOLocal]: FR[Any, Throwable, Unit] = {
      F.fromKleisli {
          F.askWith {
            _: Int =>
              ()
          }.toKleisli
        }.provide(4)
    }
    def arrowAsk[FR[-_, +_, +_]: BIOArrow: BIOAsk]: FR[String, Throwable, Int] = {
      F.askWith {
          _: Int =>
            ()
        }.dimap {
          _: String =>
            4
        }(_ => 1)
    }
    def profunctorOnly[FR[-_, +_, +_]: BIOProfunctor]: FR[String, Throwable, Int] = {
      F.contramap(??? : FR[Unit, Throwable, Int]) {
          _: Int =>
            ()
        }.dimap {
          _: String =>
            4
        }(_ => 1)
        .map(_ + 2)
    }
    def bifunctorOnly[FR[-_, +_, +_]: BIOBifunctor3]: FR[Unit, Int, Int] = {
      F.leftMap(??? : FR[Unit, Int, Int]) {
          _: Int =>
            ()
        }.bimap(
          {
            _: Unit =>
              4
          },
          _ => 1,
        )
        .map(_ + 2)
    }
    def biotemporalPlusLocal[FR[-_, +_, +_]: BIOTemporal3: BIOLocal]: FR[Any, Throwable, Unit] = {
      F.fromKleisli {
          F.askWith {
            _: Int =>
              ()
          }.toKleisli
        }.provide(4).flatMap(_ => F.unit).widenError[Throwable].leftMap(identity)
    }
    def biomonadPlusLocal[FR[-_, +_, +_]: BIOMonad3: BIOBifunctor3: BIOLocal]: FR[Any, Throwable, Unit] = {
      F.fromKleisli {
          F.askWith {
            _: Int =>
              ()
          }.toKleisli
        }.provide(4).flatMap(_ => F.unit).widenError[Throwable].leftMap(identity)
    }
    def docExamples() = {
      import izumi.functional.bio.{BIOMonad, BIOMonadAsk, BIOPrimitives, BIORef3, F}

      def adder[F[+_, +_]: BIOMonad: BIOPrimitives](i: Int): F[Nothing, Int] =
        F.mkRef(0)
          .flatMap(ref => ref.update(_ + i) *> ref.get)

      // update ref from the environment and return result
      def adderEnv[F[-_, +_, +_]: BIOMonadAsk](i: Int): F[BIORef3[F, Int], Nothing, Int] =
        F.access {
          ref =>
            for {
              _ <- ref.update(_ + i)
              res <- ref.get
            } yield res
        }
      lazy val _ = (
        adder[zio.IO](1),
        adderEnv[zio.ZIO](1),
      )
    }
    implicit val clock: zio.clock.Clock = zio.Has(zio.clock.Clock.Service.live)
    lazy val _ = (
      x[zio.ZIO],
      onlyMonadAsk[zio.ZIO],
      onlyMonadAskAccess[zio.ZIO],
      onlyAsk[zio.ZIO],
      y[zio.ZIO],
      arrowAsk[zio.ZIO],
      profunctorOnly[zio.ZIO],
      biotemporalPlusLocal[zio.ZIO],
      biomonadPlusLocal[zio.ZIO],
      bifunctorOnly[zio.ZIO],
      docExamples(),
    )
  }
}
