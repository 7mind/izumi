package izumi.fundamentals.bio.test

import monix.bio
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.duration._

class BIOSyntaxTest extends AnyWordSpec {

  implicit val clock: zio.clock.Clock = zio.Has(zio.clock.Clock.Service.live)

  "BIOParallel.zipPar/zipParLeft/zipParRight/zipWithPar is callable" in {
    import izumi.functional.bio.BIOParallel

    def x[F[+_, +_]: BIOParallel](a: F[Nothing, Unit], b: F[Nothing, Unit]) = {
      a.zipPar(b)
      a.zipParLeft(b)
      a.zipParRight(b)
      a.zipWithPar(b)((a, b) => (a, b))
      a.flatMap(_ => b)
    }

    x[zio.IO](zio.UIO.succeed(()), zio.UIO.succeed(()))
  }

  "BIOParallel3.zipPar/zipParLeft/zipParRight/zipWithPar is callable" in {
    import izumi.functional.bio.{BIOParallel3, Parallel3}

    def x[F[-_, +_, +_]: Parallel3](a: F[Any, Nothing, Unit], b: F[Any, Nothing, Unit]) = {
      a.zipPar(b)
      a.zipParLeft(b)
      a.zipParRight(b)
      a.zipWithPar(b)((a, b) => (a, b))
      a.flatMap(_ => b)
    }

    x[zio.ZIO](zio.UIO.succeed(()), zio.UIO.succeed(()))
  }

  "BIOConcurrent syntax / attachment / conversion works" in {
    import izumi.functional.bio.{BIOConcurrent, F}

    def x[F[+_, +_]: BIOConcurrent](a: F[Nothing, Unit], b: F[Nothing, Unit]) = {
      a.zipPar(b)
      a.zipParLeft(b)
      a.zipParRight(b)
      a.zipWithPar(b)((a, b) => (a, b))
      a.flatMap(_ => b)
      a.guaranteeCase(_ => a.race(b).widenError[Throwable].catchAll(_ => F.unit orElse F.uninterruptible(F.race(a, b))).void)
      F.unit
    }

    x[zio.IO](zio.UIO.succeed(()), zio.UIO.succeed(()))
  }

  "BIOConcurrent3 syntax / attachment / conversion works" in {
    import izumi.functional.bio.{BIOConcurrent3, Concurrent3, F}

    def x[F[-_, +_, +_]: Concurrent3](a: F[Any, Nothing, Unit], b: F[Any, Nothing, Unit]) = {
      a.zipPar(b)
      a.zipParLeft(b)
      a.zipParRight(b)
      a.zipWithPar(b)((a, b) => (a, b))
      a.flatMap(_ => b)
      a.guaranteeCase(_ => a.race(b).widenError[Throwable].catchAll(_ => F.unit orElse F.uninterruptible(F.race(a, b))).void)
      F.unit
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
      a.race(b)
      a.flatMap(_ => b)
    }

    x[zio.IO](zio.UIO.succeed(()), zio.UIO.succeed(()))
    x[bio.IO](bio.UIO.evalTotal(()), bio.UIO.evalTotal(()))
  }

  "BIOAsync3.race is callable along with all BIOParallel syntax" in {
    import izumi.functional.bio.{Async3, BIOAsync3}

    def x[F[-_, +_, +_]: Async3](a: F[Any, Nothing, Unit], b: F[Any, Nothing, Unit]) = {
      a.zipPar(b)
      a.zipParLeft(b)
      a.zipParRight(b)
      a.zipWithPar(b)((a, b) => (a, b))
      a.race(b)
      a.flatMap(_ => b)
    }

    x[zio.ZIO](zio.UIO.succeed(()), zio.UIO.succeed(()))
  }

  "BIO.apply is callable" in {
    import izumi.functional.bio.BIO

    class X[F[+_, +_]: BIO] {
      def hello = BIO(println("hello world!"))
    }

    assert(new X[zio.IO].hello != null)
    assert(new X[bio.IO].hello != null)
  }

  "BIO.widen/widenError is callable" in {
    import izumi.functional.bio.{BIO, F}

    def x[F[+_, +_]: BIO]: F[Throwable, AnyVal] = {
      identity[F[Throwable, AnyVal]] {
        F.pure(None: Option[Int]).flatMap {
          _.fold(
            F.unit.widenError[Throwable].widen[AnyVal]
          )(_ => F.fail(new RuntimeException))
        }
      }
    }

    x[zio.IO]
  }

  "BIOBracket.bracketCase & guaranteeCase are callable" in {
    import izumi.functional.bio.{BIOBracket, Exit, F}

    def x[F[+_, +_]: BIOBracket]: F[Throwable, Int] = {
      F.pure(None).bracketCase(release = {
          (_, _: Exit[Throwable, Int]) => F.unit
        })(_ => F.pure(1))
    }
    def y[F[+_, +_]: BIOBracket]: F[Throwable, Int] = {
      F.pure(None).bracketCase[Throwable, Int] {
          case (_, Exit.Success(x)) => F.pure(x).as(())
          case (_, _) => F.unit
        }(_ => F.pure(1))
    }
    def z[F[+_, +_]: BIOBracket]: F[Throwable, Int] = {
      F.pure(1).guaranteeCase {
        case Exit.Success(x) => F.pure(x).as(())
        case _ => F.unit
      }
    }
    def zz[F[+_, +_]: BIOBracket]: F[Throwable, Int] = {
      F.when(F.pure(false).widenError[Throwable])(F.unit).as(1).guaranteeCase {
          case Exit.Success(x) => F.pure(x).as(())
          case _ => F.unit
        }.widenError[Throwable]
    }

    x[zio.IO]
    y[zio.IO]
    z[zio.IO]
    zz[zio.IO]

    x[bio.IO]
    y[bio.IO]
    z[bio.IO]
    zz[bio.IO]
  }

  "BIO.when/unless/ifThenElse have nice inference" in {
    import izumi.functional.bio.{BIOMonad, F}

    def x[F[+_, +_]: BIOMonad] = {
      F.ifThenElse(F.pure(false): F[RuntimeException, Boolean])(F.pure(()), F.pure(()): F[Throwable, Any]) *>
      F.when(F.pure(false): F[RuntimeException, Boolean])(F.pure(()): F[Throwable, Unit])
    }

    x[zio.IO]
  }

  "withFilter test" in {
    import izumi.functional.bio.{BIOError, F}

    def x[F[+_, +_]: BIOError]: F[NoSuchElementException, Unit] = {
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
    def y[F[+_, +_]: BIOError]: F[Any, Unit] = {
      for {
        (1, 2) <- F.pure((2, 1))
      } yield ()
    }
    def z[F[+_, +_]: BIOError]: F[String, Unit] = {
      for {
        (1, 2) <- F.pure((2, 1)).widenError[String]
      } yield ()
    }
    def xx[F[+_, +_]: BIOError]: F[Unit, Unit] = {
      for {
        (1, 2) <- F.pure((2, 1)).widenError[Unit]
      } yield ()
    }
    def yy[F[+_, +_]: BIOError]: F[Option[Throwable], Unit] = {
      for {
        (1, 2) <- F.pure((2, 1)).widenError[Option[Throwable]]
      } yield ()
    }
    x[zio.IO]
    y[zio.IO]
    z[zio.IO]
    xx[zio.IO]
    yy[zio.IO]
  }

  "F summoner examples" in {
    import izumi.functional.bio.{BIOFork, BIOFunctor, BIOMonad, BIOPrimitives3, BIOTemporal, F, Fork3, Monad3, Primitives2}

    def x[F[+_, +_]: BIOMonad] = {
      F.when(false)(F.unit)
    }
    def y[F[+_, +_]: BIOTemporal: BIOFork] = {
      F.timeout(5.seconds)(F.forever(F.unit)) *>
      F.map(z[F])(_ => ())
    }
    def z[F[+_, +_]: BIOFunctor]: F[Nothing, Unit] = {
      F.map(z[F])(_ => ())
    }
    def `attach BIOPrimitives & BIOFork methods even when they aren't imported`[F[+_, +_]: BIOMonad: Primitives2: BIOFork]: F[Nothing, Int] = {
      F.fork[Any, Nothing, Int] {
        F.mkRef(4).flatMap(r => r.update(_ + 5) *> r.get.map(_ - 1))
      }.flatMap(_.join) *>
      F.mkRef(4).flatMap(r => r.update(_ + 5) *> r.get.map(_ - 1)).fork.flatMap(_.join)
    }
    def `attach BIOPrimitives & BIOFork3 methods to a trifunctor BIO even when not imported`[FR[-_, +_, +_]: Monad3: BIOPrimitives3: Fork3]: FR[Nothing, Nothing, Int] = {
      F.fork(F.mkRef(4).flatMap(r => r.update(_ + 5) *> r.get.map(_ - 1))).flatMap(_.join) *>
      F.mkRef(4).flatMap(r => r.update(_ + 5) *> r.get.map(_ - 1)).fork.flatMap(_.join)
    }
    lazy val zioTest = {
      (
        x[zio.IO],
        y[zio.IO],
        z[zio.IO],
        `attach BIOPrimitives & BIOFork methods even when they aren't imported`[zio.IO],
        `attach BIOPrimitives & BIOFork3 methods to a trifunctor BIO even when not imported`[zio.ZIO],
      )
    }

    lazy val monixTest = (
      x[bio.IO],
      y[bio.IO],
      z[bio.IO],
    )
    lazy val _ = (zioTest, monixTest)
  }

  "Support BIO syntax for ZIO with wildcard import" in {
    import izumi.functional.bio._

    zio.IO.effectTotal(List(4)).flatMap {
      F.traverse(_)(_ => zio.IO.unit)
    } *> F.unit
    implicitly[Bifunctor3[zio.ZIO]]
    implicitly[BIOBifunctor[zio.IO]]
  }

  "FR: Local/Ask summoners examples" in {
    import izumi.functional.bio.{Arrow3, Ask3, Bifunctor3, F, Local3, Monad3, MonadAsk3, Profunctor3, Temporal3}
    //
    import izumi.functional.bio.{BIOAsk, BIOBifunctor3, BIOLocal, BIOMonadAsk, BIOProfunctor, BIOTemporal3}

    def x[FR[-_, +_, +_]: Monad3: Ask3] = {
      F.unit *> F.ask[Int].map {
        _: Int =>
          true
      } *>
      F.unit *> F.askWith {
        _: Int =>
          true
      }
    }
    def onlyMonadAsk[FR[-_, +_, +_]: MonadAsk3]: FR[Int, Nothing, Unit] = {
      F.unit <* F.askWith {
        _: Int =>
          true
      }
    }
    def onlyMonadAskAccess[FR[-_, +_, +_]: MonadAsk3]: FR[Int, Nothing, Unit] = {
      F.unit <* F.access {
        _: Int =>
          F.unit
      }
    }
    def onlyAsk[FR[-_, +_, +_]: Ask3]: FR[Int, Nothing, Unit] = {
      F.askWith {
        _: Int =>
          true
      } *> F.unit
    }
    def y[FR[-_, +_, +_]: Local3]: FR[Any, Throwable, Unit] = {
      F.fromKleisli {
        F.askWith {
          _: Int =>
            ()
        }.toKleisli
      }.provide(4)
    }
    def arrowAsk[FR[-_, +_, +_]: Arrow3: Ask3]: FR[String, Throwable, Int] = {
      F.askWith {
        _: Int =>
          ()
      }.dimap {
          _: String =>
            4
        }(_ => 1)
    }
    def profunctorOnly[FR[-_, +_, +_]: Profunctor3]: FR[String, Throwable, Int] = {
      F.contramap(??? : FR[Unit, Throwable, Int]) {
        _: Int =>
          ()
      }.dimap {
          _: String =>
            4
        }(_ => 1)
        .map(_ + 2)
    }
    def bifunctorOnly[FR[-_, +_, +_]: Bifunctor3]: FR[Unit, Int, Int] = {
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
    def biotemporalPlusLocal[FR[-_, +_, +_]: Temporal3: Local3]: FR[Any, Throwable, Unit] = {
      F.fromKleisli {
        F.askWith {
          _: Int =>
            ()
        }.toKleisli
      }.provide(4).flatMap(_ => F.unit).widenError[Throwable].leftMap(identity)
    }
    def biomonadPlusLocal[FR[-_, +_, +_]: Monad3: Bifunctor3: Local3]: FR[Any, Throwable, Unit] = {
      F.fromKleisli {
        F.askWith {
          _: Int =>
            ()
        }.toKleisli
      }.provide(4).flatMap(_ => F.unit).widenError[Throwable].leftMap(identity)
    }

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
    )
  }

  "doc examples" in {
    locally {
      import izumi.functional.bio.{BIOMonad, F, Primitives2}
      def adder[F[+_, +_]: BIOMonad: Primitives2](i: Int): F[Nothing, Int] =
        F.mkRef(0)
          .flatMap(ref => ref.update(_ + i) *> ref.get)

      lazy val _ = adder[zio.IO](1)
    }

    locally {
      import izumi.functional.bio.{F, MonadAsk3, Ref3}
      import izumi.functional.bio.BIOMonadAsk
      // update ref from the environment and return result
      def adderEnv[F[-_, +_, +_]: MonadAsk3](i: Int): F[Ref3[F, Int], Nothing, Int] =
        F.access {
          ref =>
            for {
              _ <- ref.update(_ + i)
              res <- ref.get
            } yield res
        }

      lazy val _ = adderEnv[zio.ZIO](1)
    }

    locally {
      import izumi.functional.bio.{BIOTemporal, F}

      def y[F[+_, +_]: BIOTemporal] = {
        F.timeout(5.seconds)(F.forever(F.unit))
      }

      lazy val _ = (
        y[zio.IO],
        y[monix.bio.IO],
      )
    }
  }
}
