package izumi.functional.bio.test

import izumi.functional.bio.data.{Morphism1, Morphism2, Morphism3}
import izumi.functional.bio.retry.{RetryPolicy, Scheduler2}
import izumi.fundamentals.platform.language.{IzScala, ScalaRelease}
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.duration.*

class SyntaxTest extends AnyWordSpec {

  "BIOParallel.zipPar/zipParLeft/zipParRight/zipWithPar is callable" in {
    import izumi.functional.bio.Parallel2

    def x[F[+_, +_]: Parallel2](a: F[Nothing, Unit], b: F[Nothing, Unit]) = {
      a.zipPar(b)
      a.zipParLeft(b)
      a.zipParRight(b)
      a.zipWithPar(b)((a, b) => (a, b))
      a.flatMap(_ => b)
    }

    x[zio.IO](zio.ZIO.succeed(()), zio.ZIO.succeed(()))
  }

  "BIOConcurrent syntax / attachment / conversion works" in {
    import izumi.functional.bio.{Concurrent2, F}

    def x[F[+_, +_]: Concurrent2](a: F[Nothing, Unit], b: F[Nothing, Unit]) = {
      a.zipPar(b)
      a.zipParLeft(b)
      a.zipParRight(b)
      a.zipWithPar(b)((a, b) => (a, b))
      a.flatMap(_ => b)
      a.guaranteeCase(_ => a.race(b).widenError[Throwable].catchAll(_ => F.unit orElse F.uninterruptible(F.race(a, b))).void)
      F.unit
    }

    x[zio.IO](zio.ZIO.succeed(()), zio.ZIO.succeed(()))
  }

  "Async2.race is callable along with all BIOParallel syntax" in {
    import izumi.functional.bio.Async2

    def x[F[+_, +_]: Async2](a: F[Nothing, Unit], b: F[Nothing, Unit]) = {
      a zipPar b
      a zipParLeft b
      a zipParRight b
      a.zipWithPar(b)((a, b) => (a, b))
      a.race(b)
      a.flatMap(_ => b)
    }

    x[zio.IO](zio.ZIO.succeed(()), zio.ZIO.succeed(()))
//    x[bio.IO](bio.UIO.evalTotal(()), bio.UIO.evalTotal(()))
  }

  "IO2.apply is callable" in {
    import izumi.functional.bio.IO2

    class X[F[+_, +_]: IO2] {
      def hello = IO2(println("hello world!"))
    }

    assert(new X[zio.IO].hello != null)
//    assert(new X[bio.IO].hello != null)
  }

  ".widen/widenError is callable" in {
    import izumi.functional.bio.{F, IO2}

    def x[F[+_, +_]: IO2]: F[Throwable, AnyVal] = {
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

  "Bracket2.bracketCase & guaranteeCase are callable" in {
    import izumi.functional.bio.{Bracket2, Exit, F}

    def x[F[+_, +_]: Bracket2]: F[Throwable, Int] = {
      F.pure(None).bracketCase(release = {
          (_, _: Exit[Throwable, Int]) => F.unit
        })(_ => F.pure(1))
    }
    def y[F[+_, +_]: Bracket2]: F[Throwable, Int] = {
      F.pure(None).bracketCase {
          (_, exit: Exit[Throwable, Int]) =>
            exit match {
              case Exit.Success(x) => F.pure(x).as(())
              case _ => F.unit
            }
        }(_ => F.pure(1))
    }
    def z[F[+_, +_]: Bracket2]: F[Throwable, Int] = {
      F.pure(1).guaranteeCase {
        case Exit.Success(x) => F.pure(x).as(())
        case _ => F.unit
      }
    }
    def zz[F[+_, +_]: Bracket2]: F[Throwable, Int] = {
      F.when(F.pure(false).widenError[Throwable])(F.unit).as(1).guaranteeCase {
          case Exit.Success(x) => F.pure(x).as(())
          case _ => F.unit
        }.widenError[Throwable]
    }

    x[zio.IO]
    y[zio.IO]
    z[zio.IO]
    zz[zio.IO]

//    x[bio.IO]
//    y[bio.IO]
//    z[bio.IO]
//    zz[bio.IO]
  }

  "Bracket2.bracketOnFailure & guaranteeOnFailure are callable" in {
    import izumi.functional.bio.{Bracket2, Exit, F}

    def x[F[+_, +_]: Bracket2]: F[Throwable, Int] = {
      F.pure(None).bracketOnFailure(cleanupOnFailure = {
          (_, _: Exit.Failure[Throwable]) => F.unit
        })(_ => F.pure(1))
    }
    def y[F[+_, +_]: Bracket2]: F[Throwable, Int] = {
      F.pure(None).bracketOnFailure {
          (_, _: Exit.Failure[Throwable]) => F.unit
        }(_ => F.pure(1))
    }
    def z[F[+_, +_]: Bracket2]: F[Throwable, Int] = {
      F.pure(1).guaranteeOnFailure(_ => F.unit)
    }
    def zz[F[+_, +_]: Bracket2]: F[Throwable, Int] = {
      F.when(F.pure(false).widenError[Throwable])(F.unit).as(1).guaranteeOnFailure(_ => F.unit).widenError[Throwable]
    }

    x[zio.IO]
    y[zio.IO]
    z[zio.IO]
    zz[zio.IO]

//    x[bio.IO]
//    y[bio.IO]
//    z[bio.IO]
//    zz[bio.IO]
  }

  "BIO.when/unless/ifThenElse have nice inference" in {
    import izumi.functional.bio.{F, Monad2}

    def x[F[+_, +_]: Monad2] = {
      F.ifThenElse(F.pure(false): F[RuntimeException, Boolean])(F.pure(()), F.pure(()): F[Throwable, Any]) *>
      F.when(F.pure(false): F[RuntimeException, Boolean])(F.pure(()): F[Throwable, Unit])
    }

    x[zio.IO]
  }

  "withFilter test" in {
    import izumi.functional.bio.{Error2, F}

    def x[F[+_, +_]: Error2]: F[NoSuchElementException, Unit] = {
      IzScala.scalaRelease match {
        case _: ScalaRelease.`3` =>
        case _ =>
          assertDoesNotCompile("""
        for {
          (1, 2) <- F.pure(Option((2, 1)))
        } yield ()
        """)
      }
      for {
        case (1, 2) <- F.pure((2, 1))
      } yield ()
    }
    def y[F[+_, +_]: Error2]: F[Any, Unit] = {
      for {
        case (1, 2) <- F.pure((2, 1))
      } yield ()
    }
    def z[F[+_, +_]: Error2]: F[String, Unit] = {
      // Scala 3 Workaround
      import izumi.functional.bio.WithFilter.WithFilterString
      // Scala 3 Workaround

      for {
        case (1, 2) <- F.pure((2, 1)).widen[Any].widenError[String]
      } yield ()
    }
    def xx[F[+_, +_]: Error2]: F[Unit, Unit] = {
      // Scala 3 Workaround
      import izumi.functional.bio.WithFilter.WithFilterUnit
      // Scala 3 Workaround

      for {
        case (1, 2) <- F.pure((2, 1)).widen[Any].widenError[Unit]
      } yield ()
    }
    def yy[F[+_, +_]: Error2]: F[Option[Throwable], Unit] = {
      // Scala 3 Workaround
      import izumi.functional.bio.WithFilter
      implicit val withFilterScala3Workaround: WithFilter[Option[Throwable]] = WithFilter.WithFilterOption(WithFilter.WithFilterNoSuchElementException)
      val _ = withFilterScala3Workaround
      // Scala 3 Workaround

      for {
        case (1, 2) <- F.pure((2, 1)).widen[Any].widenError[Option[Throwable]]
      } yield ()
    }
    x[zio.IO]
    y[zio.IO]
    z[zio.IO]
    xx[zio.IO]
    yy[zio.IO]
  }

  "F summoner examples" in {
    import izumi.functional.bio.{F, Fork2, Functor2, Monad2, Primitives2, Temporal2, PrimitivesM2}

    def x[F[+_, +_]: Monad2] = {
      F.when(false)(F.unit)
    }
    def y[F[+_, +_]: Temporal2: Fork2] = {
      F.timeout(5.seconds)(F.forever(F.unit)) *>
      F.map(z[F])(_ => ())
    }
    def z[F[+_, +_]: Functor2]: F[Nothing, Unit] = {
      F.map(z[F])(_ => ())
    }
    def `attach Primitives2 & Fork2 methods even when they aren't imported`[F[+_, +_]: Monad2: Primitives2: Fork2]: F[Nothing, Int] = {
      F.fork[Nothing, Int] {
        F.mkRef(4).flatMap(r => r.update(_ + 5) *> r.get.map(_ - 1))
      }.flatMap(_.join) *>
      F.mkRef(4).flatMap(r => r.update(_ + 5) *> r.get.map(_ - 1)).fork.flatMap(_.join)
    }
    def `attach PrimitivesM2 methods to BIO even when not imported`[F[+_, +_]: Monad2: PrimitivesM2]: F[Nothing, Int] = {
      F.mkRefM(4).flatMap(r => r.update(_ => F.pure(5)) *> r.get.map(_ - 1)) *>
      F.mkMutex.flatMap(m => m.bracket(F.pure(10)))
    }
    def attachScheduler2[F[+_, +_]: Monad2: Scheduler2]: F[Nothing, Int] = {
      F.repeat(F.pure(42))(RetryPolicy.recurs(2))
    }
    lazy val zioTest = {
      (
        x[zio.IO],
        y[zio.IO],
        z[zio.IO],
        `attach Primitives2 & Fork2 methods even when they aren't imported`[zio.IO],
        `attach PrimitivesM2 methods to BIO even when not imported`[zio.IO],
        attachScheduler2[zio.IO],
      )
    }

    lazy val monixTest = (
//      x[bio.IO],
//      y[bio.IO],
//      z[bio.IO],
//      attachScheduler2[bio.IO],
    )

    lazy val eitherTest = (
      x[Either],
      z[Either],
    )
    lazy val _ = (zioTest, monixTest, eitherTest)
  }

  "Support BIO syntax for ZIO with wildcard import" in {
    import izumi.functional.bio._

    zio.ZIO.succeed(List(4)).flatMap {
      F.traverse(_)(_ => zio.ZIO.unit)
    } *> F.unit
  }

  "doc examples" in {
    locally {
      import izumi.functional.bio.{F, Monad2, Primitives2}
      def adder[F[+_, +_]: Monad2: Primitives2](i: Int): F[Nothing, Int] =
        F.mkRef(0)
          .flatMap(ref => ref.update(_ + i) *> ref.get)

      lazy val _ = adder[zio.IO](1)
    }

    locally {
      import izumi.functional.bio.{F, Temporal2}

      def y[F[+_, +_]: Temporal2] = {
        F.timeout(5.seconds)(F.forever(F.unit))
      }

      lazy val _ =
        y[zio.IO]
//        y[monix.bio.IO],
    }
  }

  "BIO.iterateUntil/iterateWhile are callable" in {
    import izumi.functional.bio.{Error2, Monad2}

    def x[F[+_, +_]: Monad2](a: F[Nothing, Unit]) = {
      a.iterateWhile(_ => true)
      a.iterateUntil(_ => false)
    }

    def y[F[+_, +_]: Error2](a: F[Nothing, Unit]) = {
      a.iterateWhile(_ => true)
      a.iterateUntil(_ => false)
    }

    x[zio.IO](zio.ZIO.succeed(()))
    y[zio.IO](zio.ZIO.succeed(()))
  }

  "BIO.retryUntil/retryUntilF/retryWhile/retryWhileF/fromOptionOr/fromOptionF/fromOption are callable" in {
    import izumi.functional.bio.{Error2, F, Functor2, Monad2}

    def x[F[+_, +_]: Functor2](aOpt: F[String, Option[Unit]]) = {
      aOpt.fromOptionOr(())
    }

    def y[F[+_, +_]: Monad2](aOpt: F[String, Option[Unit]]) = {
      aOpt.fromOptionOr(())
      aOpt.fromOptionF(F.unit)
    }

    def z[F[+_, +_]: Error2](a: F[String, Unit], aOpt: F[String, Option[Unit]]) = {
      a.retryUntil(_ => true)
      a.retryUntilF(_ => F.pure(false))
      a.retryWhile(_ => false)
      a.retryWhileF(_ => F.pure(true))
      aOpt.fromOptionOr(())
      aOpt.fromOptionF(F.unit)
      aOpt.fromOption("ooops")
    }

    x[zio.IO](zio.ZIO.succeed(Option(())))
    y[zio.IO](zio.ZIO.succeed(Option(())))
    z[zio.IO](zio.ZIO.succeed(()), zio.ZIO.succeed(Option(())))
  }

  "Fiber#toCats syntax works" in {
    import izumi.functional.bio.{Applicative2, F, Fork2}

    def x2[F[+_, +_]: Applicative2: Fork2] = {
      F.unit.fork.map(_.toCats)
    }
    x2[zio.IO]
  }

  "Morphism1/2/3 identity is available" in {
    implicitly[Morphism1[List, List]]
    implicitly[Morphism2[Either, Either]]
    implicitly[Morphism3[zio.ZIO, zio.ZIO]]
  }

  "BIO.clock/entropy are callable" in {
    import izumi.functional.bio.{F, Functor2, Temporal2, Clock2, Entropy2}

    def x[F[+_, +_]: Temporal2: Clock2] = {
      F.clock.now()
    }

    def y[F[+_, +_]: Functor2: Clock2] = {
      F.clock.now()
    }

    def z[F[+_, +_]: Functor2: Entropy2] = {
      F.entropy.nextInt()
    }

    x[zio.IO]
    y[zio.IO]
    z[zio.IO]
    F.clock.now()
  }

}
