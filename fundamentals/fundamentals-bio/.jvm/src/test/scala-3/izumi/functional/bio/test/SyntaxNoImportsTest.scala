package izumi.functional.bio.test

// Intentionally: no `import izumi.functional.bio.{Monad2, Parallel2, …}`.
// Every test below is a verbatim replication of the corresponding test in
// [[SyntaxTest]] except that all typeclass names are fully qualified. The
// point is to exercise "case 1" of the Scala 3 BIO syntax design — where
// extension methods must resolve even when no typeclass name is in lexical
// scope (so none of the `Syntax2.ImplicitPuns` pun carrier `given`s are in
// scope either). Each typeclass given arrives via a context bound alone;
// the extensions that resolve inside the method body must therefore be
// coming from mechanism #1 — each typeclass trait mixes in its own-level
// `<Xyz>2ExtensionMethods`, so a `given Xyz2[F]` carries the applicable
// extensions as members (propagated up the typeclass hierarchy by Scala's
// usual subtype-member resolution).
//
// Sibling typeclasses (`Parallel2`, `Concurrent2`, `Temporal2`, `WeakTemporal2`,
// `ParallelErrorAccumulatingOps2`) carry their `Monad2`/`Error2`/`Panic2`
// parent via an `InnerF` member rather than direct typeclass inheritance, so
// wherever a test exercises shared-ancestor extensions (`.flatMap`,
// `.catchAll`, `.orElse`, `.widenError`, …) under such a sibling bound we
// publish the parent as an `implicit val` promoted from `InnerF`.
import izumi.functional.bio.F
import org.scalatest.wordspec.AnyWordSpec

import scala.annotation.unused
import scala.concurrent.duration.DurationInt

class SyntaxNoImportsTest extends AnyWordSpec {

  "BIOParallel attachment/conversion works zipPar/zipParLeft/zipParRight/zipWithPar is callable" in {
    def x[F[+_, +_]: izumi.functional.bio.Parallel2](a: F[Nothing, Unit], b: F[Nothing, Unit]) = {
      implicit val _M: izumi.functional.bio.Monad2[F] = summon[izumi.functional.bio.Parallel2[F]].InnerF

      a.zipPar(b)
      a.zipParLeft(b)
      a.zipParRight(b)
      a.zipWithPar(b)((a, b) => (a, b))
      a.flatMap(_ => b).flatMap(_ => F.unit)
      F.unit: F[Nothing, Unit]
    }

    def y[F[+_, +_]: izumi.functional.bio.Parallel2]: F[Nothing, Unit] = {
      F.parTraverse_(List(1))(_ => F.unit)
    }

    x[zio.IO](zio.ZIO.succeed(()), zio.ZIO.succeed(()))
    y[zio.IO]
  }

  "WeakAsync attachment/conversion works" in {
    import java.io.Closeable

    def x[F[+_, +_]: izumi.functional.bio.WeakAsync2](a: F[Nothing, Unit], b: F[Nothing, Unit], c: F[Throwable, Closeable]) = {
      a.zipPar(b)
      a.zipParLeft(b)
      a.zipParRight(b)
      a.zipWithPar(b)((a, b) => (a, b))
      c.bracketAuto(_ => F.unit.flatMap(_ => F.unit).uninterruptible)
      F.syncThrowable(())
      F.sync(())
      F.never
      F.orTerminateK
      F.orTerminateCats
      F.unit: F[Nothing, Unit]
    }

    def y[F[+_, +_]: izumi.functional.bio.WeakAsync2]: F[Nothing, Unit] = {
      F.parTraverse_(List(1))(_ => F.unit)
    }

    x[zio.IO](zio.ZIO.succeed(()), zio.ZIO.succeed(()), zio.ZIO.succeed(null: Closeable))
    y[zio.IO]
  }

  "BIOConcurrent attachment/conversion works" in {
    def x[F[+_, +_]: izumi.functional.bio.Concurrent2](a: F[Nothing, Unit], b: F[Nothing, Unit]) = {
      implicit val _P: izumi.functional.bio.Panic2[F] = summon[izumi.functional.bio.Concurrent2[F]].InnerF

      a.zipPar(b)
      a.zipParLeft(b)
      a.zipParRight(b)
      a.zipWithPar(b)((a, b) => (a, b))
      a.flatMap(_ => b).flatMap(_ => F.unit)
      a.guaranteeCase(_ => a.race(b).widenError[Throwable].catchAll(_ => F.unit `orElse` F.uninterruptible(F.race(a, b))).void)
      F.fail("x"): F[String, Unit]
      F.orTerminateK
      F.orTerminateCats
      F.unit: F[Nothing, Unit]
    }

    def y[F[+_, +_]: izumi.functional.bio.Concurrent2]: F[Nothing, Unit] = {
      F.parTraverse_(List(1))(_ => F.unit)
      F.yieldNow
    }

    x[zio.IO](zio.ZIO.succeed(()), zio.ZIO.succeed(()))
    y[zio.IO]
  }

  "ParallelErrorAccumulatingOps2 attachment/conversion works" in {
    def x[F[+_, +_]: izumi.functional.bio.ParallelErrorAccumulatingOps2](a: F[Nothing, Unit], b: F[Nothing, Unit]) = {
      implicit val _E: izumi.functional.bio.Error2[F] = summon[izumi.functional.bio.ParallelErrorAccumulatingOps2[F]].InnerF

      a.zipPar(b)
      a.zipParLeft(b)
      a.zipParRight(b)
      a.zipWithPar(b)((a, b) => (a, b))
      a.flatMap(_ => b).flatMap(_ => F.unit)
      F.fail("x"): F[String, Unit]
      F.unit: F[Nothing, Unit]
    }

    def y[F[+_, +_]: izumi.functional.bio.ParallelErrorAccumulatingOps2]: F[Nothing, Unit] = {
      F.parTraverse_(List(1))(_ => F.unit)
    }

    def a[F[+_, +_]: izumi.functional.bio.ParallelErrorAccumulatingOps2]: F[List[Int], Int] = {
      implicit val _E: izumi.functional.bio.Error2[F] = summon[izumi.functional.bio.ParallelErrorAccumulatingOps2[F]].InnerF

      F.parTraverseAccumErrors(List(1))(_ => a[F]).map(_.head)
    }

    x[zio.IO](zio.ZIO.succeed(()), zio.ZIO.succeed(()))
    y[zio.IO]
    a[zio.IO]
  }

  "WeakTemporal2 attachment/conversion works" in {
    def x[F[+_, +_]: izumi.functional.bio.WeakTemporal2](a: F[Nothing, Unit], b: F[Nothing, Unit]) = {
      a.flatMap(_ => b).flatMap(_ => F.unit)
      a.widenError[Throwable].catchAll(_ => F.unit `orElse` b).void
      F.unit: F[Nothing, Unit]

      F.pure(Some(1)).repeatUntil("error", 5.seconds, 10)
      F.sleep(5.seconds): F[Nothing, Unit]
    }

    x[zio.IO](zio.ZIO.succeed(()), zio.ZIO.succeed(()))
  }

  "BIOTemporal attachment/conversion works" in {
    def x[F[+_, +_]: izumi.functional.bio.Temporal2](a: F[Nothing, Unit], b: F[Nothing, Unit]) = {
      a.flatMap(_ => b).flatMap(_ => F.unit)
      a.widenError[Throwable].catchAll(_ => F.unit `orElse` b).void
      F.unit: F[Nothing, Unit]

      F.pure(Some(1)).repeatUntil("error", 5.seconds, 10)
      F.sleep(5.seconds): F[Nothing, Unit]
      F.timeout(5.seconds)(F.forever(F.unit)): F[Nothing, Option[Unit]]
    }

    x[zio.IO](zio.ZIO.succeed(()), zio.ZIO.succeed(()))
  }

  "BIOConcurrent conversion works in presence of BIOParallel/BIOTemporal, overrides BIOParallel" in {
    def x[F[+_, +_]: izumi.functional.bio.Concurrent2: izumi.functional.bio.Temporal2](
      a: F[Nothing, Unit],
      b: F[Nothing, Unit],
    )(implicit @unused P: izumi.functional.bio.Parallel2[F]
    ) = {
      implicit val _P: izumi.functional.bio.Panic2[F] = summon[izumi.functional.bio.Concurrent2[F]].InnerF

      a.zipPar(b)
      a.zipParLeft(b)
      a.zipParRight(b)
      a.zipWithPar(b)((a, b) => (a, b))
      a.flatMap(_ => b).flatMap(_ => F.unit)
      a.guaranteeCase(_ => a.race(b).widenError[Throwable].catchAll(_ => F.unit `orElse` F.uninterruptible(F.race(a, b))).void)
      F.unit: F[Nothing, Unit]

      F.sleep(5.seconds): F[Nothing, Unit]
      F.timeout(5.seconds)(F.forever(F.unit)): F[Nothing, Option[Unit]]
    }

    x[zio.IO](zio.ZIO.succeed(()), zio.ZIO.succeed(()))
  }

  "Async2.race is callable along with all BIOParallel syntax" in {
    def x[F[+_, +_]: izumi.functional.bio.Async2](a: F[Nothing, Unit], b: F[Nothing, Unit]) = {
      a zipPar b
      a zipParLeft b
      a zipParRight b
      a.zipWithPar(b)((a, b) => (a, b))
      a.race(b)
      a.flatMap(_ => b)
    }

    x[zio.IO](zio.ZIO.succeed(()), zio.ZIO.succeed(()))
  }

  "IO2.apply is callable" in {
    class X[F[+_, +_]: izumi.functional.bio.IO2] {
      def hello: F[Throwable, Unit] = izumi.functional.bio.IO2[F, Unit](println("hello world!"))
    }

    assert(new X[zio.IO].hello != null)
  }

  ".widen/widenError is callable" in {
    def x[F[+_, +_]: izumi.functional.bio.IO2]: F[Throwable, AnyVal] = {
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
    def x[F[+_, +_]: izumi.functional.bio.Bracket2]: F[Throwable, Int] = {
      F
        .pure(None).bracketCase(release = {
          (_, _: izumi.functional.bio.Exit[Throwable, Int]) => F.unit
        })(_ => F.pure(1))
    }
    def y[F[+_, +_]: izumi.functional.bio.Bracket2]: F[Throwable, Int] = {
      F
        .pure(None).bracketCase {
          (_, exit: izumi.functional.bio.Exit[Throwable, Int]) =>
            exit match {
              case izumi.functional.bio.Exit.Success(x) => F.pure(x).as(())
              case _ => F.unit
            }
        }(_ => F.pure(1))
    }
    def z[F[+_, +_]: izumi.functional.bio.Bracket2]: F[Throwable, Int] = {
      F.pure(1).guaranteeCase {
        case izumi.functional.bio.Exit.Success(x) => F.pure(x).as(())
        case _ => F.unit
      }
    }
    def zz[F[+_, +_]: izumi.functional.bio.Bracket2]: F[Throwable, Int] = {
      F
        .when(F.pure(false).widenError[Throwable])(F.unit).as(1).guaranteeCase {
          case izumi.functional.bio.Exit.Success(x) => F.pure(x).as(())
          case _ => F.unit
        }.widenError[Throwable]
    }

    x[zio.IO]
    y[zio.IO]
    z[zio.IO]
    zz[zio.IO]
  }

  "Bracket2.bracketOnFailure & guaranteeOnFailure are callable" in {
    def x[F[+_, +_]: izumi.functional.bio.Bracket2]: F[Throwable, Int] = {
      F
        .pure(None).bracketOnFailure(cleanupOnFailure = {
          (_, _: izumi.functional.bio.Exit.Failure[Throwable]) => F.unit
        })(_ => F.pure(1))
    }
    def y[F[+_, +_]: izumi.functional.bio.Bracket2]: F[Throwable, Int] = {
      F
        .pure(None).bracketOnFailure {
          (_, _: izumi.functional.bio.Exit.Failure[Throwable]) => F.unit
        }(_ => F.pure(1))
    }
    def z[F[+_, +_]: izumi.functional.bio.Bracket2]: F[Throwable, Int] = {
      F.pure(1).guaranteeOnFailure(_ => F.unit)
    }
    def zz[F[+_, +_]: izumi.functional.bio.Bracket2]: F[Throwable, Int] = {
      F.when(F.pure(false).widenError[Throwable])(F.unit).as(1).guaranteeOnFailure(_ => F.unit).widenError[Throwable]
    }

    x[zio.IO]
    y[zio.IO]
    z[zio.IO]
    zz[zio.IO]
  }

  "BIO.when/unless/ifThenElse have nice inference" in {
    def x[F[+_, +_]: izumi.functional.bio.Monad2] = {
      F.ifThenElse(F.pure(false): F[RuntimeException, Boolean])(F.pure(()), F.pure(()): F[Throwable, Any]) *>
      F.when(F.pure(false): F[RuntimeException, Boolean])(F.pure(()): F[Throwable, Unit])
    }

    x[zio.IO]
  }

  "withFilter test" in {
    def x[F[+_, +_]: izumi.functional.bio.Error2]: F[NoSuchElementException, Unit] = {
      for {
        case (1, 2) <- F.pure((2, 1))
      } yield ()
    }
    def y[F[+_, +_]: izumi.functional.bio.Error2]: F[Any, Unit] = {
      for {
        case (1, 2) <- F.pure((2, 1))
      } yield ()
    }
    def z[F[+_, +_]: izumi.functional.bio.Error2]: F[String, Unit] = {
      for {
        case (1, 2) <- F.pure((2, 1)).widen[Any].widenError[String]
      } yield ()
    }
    def xx[F[+_, +_]: izumi.functional.bio.Error2]: F[Unit, Unit] = {
      for {
        case (1, 2) <- F.pure((2, 1)).widen[Any].widenError[Unit]
      } yield ()
    }
    def yy[F[+_, +_]: izumi.functional.bio.Error2]: F[Option[Throwable], Unit] = {
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
    def x[F[+_, +_]: izumi.functional.bio.Monad2] = {
      F.when(false)(F.unit)
    }
    def y[F[+_, +_]: izumi.functional.bio.Temporal2: izumi.functional.bio.Fork2] = {
      implicit val _E: izumi.functional.bio.Error2[F] = summon[izumi.functional.bio.Temporal2[F]].InnerF

      F.timeout(5.seconds)(F.forever(F.fork(F.unit))) *>
      F.map(z[F])(_ => ())
    }
    def z[F[+_, +_]: izumi.functional.bio.Functor2]: F[Nothing, Unit] = {
      F.map(z[F])(_ => ())
    }
    def `attach Primitives2 & Fork2 methods even when they aren't imported`[
      F[+_, +_]: izumi.functional.bio.Monad2: izumi.functional.bio.Primitives2: izumi.functional.bio.Fork2
    ]: F[Nothing, Int] = {
      F
        .fork[Nothing, Int] {
          F.mkRef(4).flatMap(r => r.update(_ + 5) *> r.get.map(_ - 1))
        }.flatMap(_.join) *>
      F.mkRef(4).flatMap(r => r.update(_ + 5) *> r.get.map(_ - 1)).fork.flatMap(_.join)
    }
    def `attach PrimitivesM2 methods to BIO even when not imported`[F[+_, +_]: izumi.functional.bio.Monad2: izumi.functional.bio.PrimitivesM2]: F[Nothing, Int] = {
      F.mkRefM(4).flatMap(r => r.update(_ => F.pure(5)) *> r.get.map(_ - 1)) *>
      F.mkMutex.flatMap(m => m.bracket(F.pure(10)))
    }
    def `attach PrimitivesLocal2 methods to BIO even when not imported`[
      F[+_, +_]: izumi.functional.bio.Monad2: izumi.functional.bio.PrimitivesLocal2
    ]: F[Nothing, Int] = {
      F.mkFiberRef(4).flatMap(r => r.update(_ + 5) *> r.get.map(_ - 1)) *>
      F.mkFiberLocal(4).flatMap(m => m.locally(10)(m.get))
    }
    def attachScheduler2[F[+_, +_]: izumi.functional.bio.Monad2: izumi.functional.bio.retry.Scheduler2]: F[Nothing, Int] = {
      F.repeat(F.pure(42))(izumi.functional.bio.retry.RetryPolicy.recurs(2))
    }
    lazy val zioTest = {
      (
        x[zio.IO],
        y[zio.IO],
        z[zio.IO],
        `attach Primitives2 & Fork2 methods even when they aren't imported`[zio.IO],
        `attach PrimitivesM2 methods to BIO even when not imported`[zio.IO],
        `attach PrimitivesLocal2 methods to BIO even when not imported`[zio.IO],
        attachScheduler2[zio.IO],
      )
    }

    lazy val eitherTest = (
      x[Either],
      z[Either],
    )
    val _ = () => (zioTest, eitherTest)
  }

  "doc examples" in {
    locally {
      def adder[F[+_, +_]: izumi.functional.bio.Monad2: izumi.functional.bio.Primitives2](i: Int): F[Nothing, Int] =
        F
          .mkRef(0)
          .flatMap(ref => ref.update(_ + i) *> ref.get)

      val _ = adder[zio.IO](1)
    }

    locally {
      def y[F[+_, +_]: izumi.functional.bio.Temporal2] = {
        F.timeout(5.seconds)(F.forever(F.unit))
      }

      val _ = y[zio.IO]
    }
  }

  "BIO.iterateUntil/iterateWhile are callable" in {
    def x[F[+_, +_]: izumi.functional.bio.Monad2](a: F[Nothing, Unit]) = {
      a.iterateWhile(_ => true)
      a.iterateUntil(_ => false)
    }

    def y[F[+_, +_]: izumi.functional.bio.Error2](a: F[Nothing, Unit]) = {
      a.iterateWhile(_ => true)
      a.iterateUntil(_ => false)
    }

    x[zio.IO](zio.ZIO.succeed(()))
    y[zio.IO](zio.ZIO.succeed(()))
  }

  "BIO.retryUntil/retryUntilF/retryWhile/retryWhileF/fromOptionOr/fromOptionF/fromOption are callable" in {
    def x[F[+_, +_]: izumi.functional.bio.Functor2](aOpt: F[String, Option[Option[Unit]]]): F[String, Option[Unit]] = {
      aOpt.fromOptionOr(None)
      aOpt.fromOptionOr(Option(()))
      aOpt.fromOptionOr(Option(5)): F[String, Option[AnyVal]]
      aOpt.fromOptionOr(None)
    }

    def y[F[+_, +_]: izumi.functional.bio.Monad2](aOpt: F[String, Option[Option[Unit]]]): F[String, Option[Unit]] = {
      aOpt.fromOptionOr(None)
      aOpt.fromOptionF(F.pure(Option(())))
      aOpt.fromOptionF(F.pure(Option(5))): F[String, Option[AnyVal]]
      aOpt.fromOptionF(F.pure(None))
    }

    def z[F[+_, +_]: izumi.functional.bio.Error2](a: F[String, Unit], aOpt: F[String, Option[Option[Unit]]]) = {
      a.retryUntil(_ => true)
      a.retryUntilF(_ => F.pure(false))
      a.retryWhile(_ => false)
      a.retryWhileF(_ => F.pure(true))
      aOpt.fromOptionOr(None)
      aOpt.fromOption("ooops")
    }

    x[zio.IO](zio.ZIO.succeed(Option(Option(()))))
    y[zio.IO](zio.ZIO.succeed(Option(Option(()))))
    z[zio.IO](zio.ZIO.succeed(()), zio.ZIO.succeed(Option(Option(()))))
  }

  "Fiber#toCats syntax works" in {
    def x2[F[+_, +_]: izumi.functional.bio.Applicative2: izumi.functional.bio.Fork2] = {
      F.unit.fork.map(_.toCats)
    }
    x2[zio.IO]
  }

  "BIO.clock/entropy are callable" in {
    def x[F[+_, +_]: izumi.functional.bio.Temporal2: izumi.functional.bio.Clock2] = {
      F.clock.nowZoned()
    }

    def y[F[+_, +_]: izumi.functional.bio.Functor2: izumi.functional.bio.Clock2] = {
      F.clock.nowZoned()
    }

    def z[F[+_, +_]: izumi.functional.bio.Functor2: izumi.functional.bio.Entropy2] = {
      F.entropy.nextInt()
    }

    x[zio.IO]
    y[zio.IO]
    z[zio.IO]
    F.clock.nowZoned()
  }

  "unsafe.maybeSuspend is callable" in {
    import izumi.functional.bio.unsafe.MaybeSuspend2

    def x[F[+_, +_]: izumi.functional.bio.Applicative2](implicit F0: MaybeSuspend2[F]): F[Nothing, Int] = {
      F.maybeSuspend(scala.util.Random.nextLong()) *>
      F0.maybeSuspend(scala.util.Random.nextInt())
    }

    x[zio.IO]
  }

  "Functor2 extensions reachable via implicit-in-scope only" in {
    def exercise[F[+_, +_]: izumi.functional.bio.Functor2](a: F[Nothing, Int], opt: F[Nothing, Option[Int]]) = {
      a.map(_ + 1)
      a.as("x")
      a.void
      a.widen[AnyVal]
      opt.fromOptionOr(0)
    }
    exercise[zio.IO](zio.ZIO.succeed(1), zio.ZIO.succeed(Option(1)))
  }

  "Applicative2 extensions reachable via implicit-in-scope only" in {
    def exercise[F[+_, +_]: izumi.functional.bio.Applicative2](a: F[Nothing, Int], b: F[Nothing, Int]) = {
      a *> b
      a <* b
      a.zip(b)
      a.map2(b)(_ + _)
      a.forever: F[Nothing, Nothing]
      // Inherited from Functor2 via typeclass hierarchy
      a.map(_ + 1)
      a.void
    }
    exercise[zio.IO](zio.ZIO.succeed(1), zio.ZIO.succeed(2))
  }

  "Monad2 extensions reachable via implicit-in-scope only" in {
    def exercise[F[+_, +_]: izumi.functional.bio.Monad2](a: F[Nothing, Int], b: F[Nothing, Int], opt: F[Nothing, Option[Int]]) = {
      a.flatMap(_ => b)
      a.tap(_ => F.unit)
      F.pure(F.pure(1)).flatten
      a.iterateWhile(_ => true)
      a.iterateUntil(_ => false)
      opt.fromOptionF(F.pure(2))
      // Inherited
      a *> b
      a.map(_ + 1)
    }
    exercise[zio.IO](zio.ZIO.succeed(1), zio.ZIO.succeed(2), zio.ZIO.succeed(Option(1)))
  }

  "Bifunctor2 extensions reachable via implicit-in-scope only" in {
    def exercise[F[+_, +_]: izumi.functional.bio.Bifunctor2](a: F[String, Int]) = {
      a.leftMap(_.length)
      a.bimap(_.length, _ + 1)
      a.widenError[CharSequence]
      a.widenBoth[CharSequence, AnyVal]
    }
    exercise[zio.IO](zio.ZIO.succeed(1))
  }

  "Guarantee2 extensions reachable via implicit-in-scope only" in {
    def exercise[F[+_, +_]: izumi.functional.bio.Guarantee2](a: F[Nothing, Int], cleanup: F[Nothing, Unit]) = {
      a.guarantee(cleanup)
      // Inherited
      a.map(_ + 1)
      a.forever
    }
    exercise[zio.IO](zio.ZIO.succeed(1), zio.ZIO.unit)
  }

  "ApplicativeError2 extensions reachable via implicit-in-scope only" in {
    def exercise[F[+_, +_]: izumi.functional.bio.ApplicativeError2](a: F[String, Int], b: F[String, Int]) = {
      a.orElse(b)
      a.leftMap2(b)((e1, e2) => e1 + e2)
      // Inherited from Guarantee2 + Bifunctor2
      a.leftMap(_.length)
      a.bimap(_.length, _ + 1)
      a.widenError[CharSequence]
    }
    exercise[zio.IO](zio.ZIO.fail("x"), zio.ZIO.fail("y"))
  }

  "Error2 extensions reachable via implicit-in-scope only" in {
    def exercise[F[+_, +_]: izumi.functional.bio.Error2](a: F[String, Int]) = {
      a.catchAll(_ => F.pure(0))
      a.catchSome { case _ => F.pure(0) }
      a.attempt
      a.redeem(_ => F.pure(0), F.pure(_))
      a.redeemPure(_.length, identity)
      a.tapError(_ => F.unit)
      a.leftFlatMap(e => F.pure(e.length))
      a.flip
      a.tapBoth(_ => F.unit)(_ => F.unit)
      a.retryWhile(_ => false)
      a.retryWhileF(_ => F.pure(true))
      a.retryUntil(_ => true)
      a.retryUntilF(_ => F.pure(false))
      // Inherited
      a.flatMap(_ => a)
      a.orElse(a)
      a.widenError[CharSequence]
    }
    exercise[zio.IO](zio.ZIO.fail("x"))
  }

  "Bracket2 extensions reachable via implicit-in-scope only" in {
    def exercise[F[+_, +_]: izumi.functional.bio.Bracket2](a: F[Nothing, Int]) = {
      a.bracket((_: Int) => F.unit)(_ => F.pure(1))
      a.bracketCase((_: Int, _) => F.unit)(_ => F.pure(1))
      a.guaranteeCase(_ => F.unit)
      a.bracketOnFailure((_: Int, _) => F.unit)(_ => F.pure(1))
      a.guaranteeOnFailure(_ => F.unit)
      a.guaranteeOnInterrupt(_ => F.unit)
      a.guaranteeExceptOnInterrupt(_ => F.unit)
      // Inherited
      a.catchAll((_: Nothing) => F.pure(0))
      a.flatMap(_ => a)
    }
    exercise[zio.IO](zio.ZIO.succeed(1))
  }

  "Panic2 extensions reachable via implicit-in-scope only" in {
    def exercise[F[+_, +_]: izumi.functional.bio.Panic2](a: F[Throwable, Int]) = {
      a.sandbox
      a.sandboxExit
      a.sandboxToThrowable
      a.orTerminate
      a.uninterruptible
      // Inherited
      a.bracket((_: Int) => F.unit)(_ => F.pure(1))
      a.flatMap(_ => a)
    }
    exercise[zio.IO](zio.ZIO.succeed(1))
  }

  "IO2 extensions reachable via implicit-in-scope only" in {
    def exercise[F[+_, +_]: izumi.functional.bio.IO2](a: F[Throwable, java.io.Closeable]) = {
      a.bracketAuto(_ => F.unit.flatMap(_ => F.unit).uninterruptible)
      // Inherited
      a.catchAll(_ => F.pure(null: java.io.Closeable))
    }
    exercise[zio.IO](zio.ZIO.succeed(null: java.io.Closeable))
  }

  "Parallel2 extensions reachable via implicit-in-scope only" in {
    def exercise[F[+_, +_]: izumi.functional.bio.Parallel2](a: F[Nothing, Unit], b: F[Nothing, Unit]) = {
      a.zipPar(b)
      a.zipParLeft(b)
      a.zipParRight(b)
      a.zipWithPar(b)((x, y) => (x, y))
    }
    exercise[zio.IO](zio.ZIO.unit, zio.ZIO.unit)
  }

  "Concurrent2 extensions reachable via implicit-in-scope only" in {
    def exercise[F[+_, +_]: izumi.functional.bio.Concurrent2](a: F[Nothing, Unit], b: F[Nothing, Unit]) = {
      a.race(b)
      a.racePairUnsafe(b)
      // Inherited from Parallel2
      a.zipPar(b)
    }
    exercise[zio.IO](zio.ZIO.unit, zio.ZIO.unit)
  }

  "WeakTemporal2 extensions reachable via implicit-in-scope only" in {
    def exercise[F[+_, +_]: izumi.functional.bio.WeakTemporal2](a: F[Nothing, Option[Int]]) = {
      a.repeatUntil("error", 1.second, 3)
    }
    exercise[zio.IO](zio.ZIO.succeed(Option(1)))
  }

  "Temporal2 extensions reachable via implicit-in-scope only" in {
    def exercise[F[+_, +_]: izumi.functional.bio.Temporal2](a: F[Nothing, Int], opt: F[Nothing, Option[Int]]) = {
      a.timeout(5.seconds)
      a.timeoutFail("e")(5.seconds)
      // Inherited from WeakTemporal2
      opt.repeatUntil("error", 1.second, 3)
    }
    exercise[zio.IO](zio.ZIO.succeed(1), zio.ZIO.succeed(Option(1)))
  }

  "Fork2 extensions reachable via implicit-in-scope only" in {
    def exercise[F[+_, +_]: izumi.functional.bio.Fork2](a: F[Nothing, Int]) = {
      a.fork
    }
    exercise[zio.IO](zio.ZIO.succeed(1))
  }

  "ParallelErrorAccumulatingOps2 extensions reachable via implicit-in-scope only" in {
    def exercise[F[+_, +_]: izumi.functional.bio.ParallelErrorAccumulatingOps2](a: F[Nothing, Unit], b: F[Nothing, Unit]) = {
      // Inherited from Parallel2 via typeclass hierarchy
      a.zipPar(b)
      a.zipParLeft(b)
      a.zipParRight(b)
    }
    exercise[zio.IO](zio.ZIO.unit, zio.ZIO.unit)
  }

  "Receiver carries extensions when inherited from a parent" in {
    trait ServiceBase[F[+_, +_]] {
      implicit def F: izumi.functional.bio.Monad2[F]
      def manufactureMissiles(price: Int): F[Throwable, Int]
      def launchMissiles(n: Int): F[Throwable, Unit]
    }
    abstract class ServiceImpl[F[+_, +_]] extends ServiceBase[F] {
      def doBusinessTask(price: Int): F[Throwable, Unit] =
        manufactureMissiles(price).flatMap(launchMissiles)
    }
    @unused val _ = null.asInstanceOf[ServiceImpl[zio.IO]]
  }

}
