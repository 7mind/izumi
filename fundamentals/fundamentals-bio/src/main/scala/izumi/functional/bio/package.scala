package izumi.functional

import cats.~>
import izumi.functional.bio.impl.{BIOAsyncZio, BIOZio}
import izumi.functional.mono.{Clock, Entropy, SyncSafe}
import zio.ZIO

import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.language.implicitConversions
import scala.util.Try

package object bio extends BIOSyntax {

  /**
    * A convenient dependent summoner for BIO* hierarchy.
    * Auto-narrows to the most powerful available class:
    *
    * {{{
    *   def y[F[+_, +_]: BIOAsync] = {
    *     F.timeout(5.seconds)(F.forever(F.unit))
    *   }
    * }}}
    *
    */
  @inline override final def F[F[+_, +_]](implicit F: BIOFunctor[F]): F.type = F


  /**
   * NOTE: The left type parameter is not forced to be covariant
   * because [[BIOFunctor]] does not yet expose any operations
   * on it.
   * */
  trait BIOFunctor[F[_, +_]] extends BIOFunctorInstances {
    def map[E, A, B](r: F[E, A])(f: A => B): F[E, B]
    def void[E, A](r: F[E, A]): F[E, Unit] = map(r)(_ => ())
  }

  private[bio] sealed trait BIOFunctorInstances
  object BIOFunctorInstances {
    // place ZIO instance at the root of hierarchy, so that it's visible when summoning any class in hierarchy
    @inline implicit final def BIOZIO[R]: BIOZio[R] = BIOZio.asInstanceOf[BIOZio[R]]

    @inline implicit def AttachBIOPrimitives[F[+_, +_]](@deprecated("unused","") self: BIOFunctor[F])(implicit BIOPrimitives: BIOPrimitives[F]): BIOPrimitives.type = BIOPrimitives

    @inline implicit def AttachBIOFork[F[+_, +_]](@deprecated("unused","") self: BIOFunctor[F])(implicit BIOFork: BIOFork[F]): BIOFork.type = BIOFork
    @inline implicit def AttachBIOFork3[F[-_, +_, +_]](@deprecated("unused","") self: BIOFunctor[F[Any, +?, +?]])(implicit BIOFork: BIOFork3[F]): BIOFork.type = BIOFork
  }

  trait BIOBifunctor[F[+_, +_]] extends BIOFunctor[F] {
    def bimap[E, A, E2, A2](r: F[E, A])(f: E => E2, g: A => A2): F[E2, A2]
    @inline def leftMap[E, A, E2](r: F[E, A])(f: E => E2): F[E2, A] = bimap(r)(f, identity)
  }

  trait BIOApplicative[F[+_, +_]] extends BIOBifunctor[F] {
    def pure[A](a: A): F[Nothing, A]

    /** execute two operations in order, map their results */
    def map2[E, A, E2 >: E, B, C](firstOp: F[E, A], secondOp: => F[E2, B])(f: (A, B) => C): F[E2, C]

    /** execute two operations in order, return result of second operation */
    def *>[E, A, E2 >: E, B](firstOp: F[E, A], secondOp: => F[E2, B]): F[E2, B]

    /** execute two operations in order, same as `*>`, but return result of first operation */
    def <*[E, A, E2 >: E, B](firstOp: F[E, A], secondOp: => F[E2, B]): F[E2, A]

    def traverse[E, A, B](l: Iterable[A])(f: A => F[E, B]): F[E, List[B]]

    @inline final def forever[E, A](r: F[E, A]): F[E, Nothing] = *>(r, forever(r))
    @inline final def traverse_[E, A, B](l: Iterable[A])(f: A => F[E, B]): F[E, Unit] = void(traverse(l)(f))
    @inline final def sequence[E, A, B](l: Iterable[F[E, A]]): F[E, List[A]] = traverse(l)(identity)
    @inline final def sequence_[E](l: Iterable[F[E, Unit]]): F[E, Unit] = void(traverse(l)(identity))

    @inline final val unit: F[Nothing, Unit] = pure(())
    @inline final def when[E](p: Boolean)(r: F[E, Unit]): F[E, Unit] = if (p) r else unit
  }

  trait BIOGuarantee[F[+_, +_]] extends BIOApplicative[F]  {
    def guarantee[E, A](f: F[E, A])(cleanup: F[Nothing, Unit]): F[E, A]
  }

  trait BIOError[F[+_ ,+_]] extends BIOGuarantee[F] {
    def fail[E](v: => E): F[E, Nothing]
    def redeem[E, A, E2, B](r: F[E, A])(err: E => F[E2, B], succ: A => F[E2, B]): F[E2, B]
    def catchSome[E, A, E2 >: E, A2 >: A](r: F[E, A])(f: PartialFunction[E, F[E2, A2]]): F[E2, A2]

    def fromEither[E, V](effect: => Either[E, V]): F[E, V]
    def fromOption[E, A](errorOnNone: => E)(effect: => Option[A]): F[E, A]
    def fromTry[A](effect: => Try[A]): F[Throwable, A]

    @inline def redeemPure[E, A, B](r: F[E, A])(err: E => B, succ: A => B): F[Nothing, B] = redeem(r)(err.andThen(pure), succ.andThen(pure))
    @inline def attempt[E, A](r: F[E, A]): F[Nothing, Either[E, A]] = redeemPure(r)(Left(_), Right(_))
    @inline def catchAll[E, A, E2, A2 >: A](r: F[E, A])(f: E => F[E2, A2]): F[E2, A2] = redeem(r)(f, pure)
    @inline def flip[E, A](r: F[E, A]) : F[A, E] = redeem(r)(pure, fail(_))
    @inline def tapError[E, E1 >: E, A](r: F[E, A])(f: E => F[E1, Unit]): F[E1, A] = catchAll(r)(e => *>(f(e), fail(e)))

    // defaults
    @inline override def bimap[E, A, E2, B](r: F[E, A])(f: E => E2, g: A => B): F[E2, B] = redeem(r)(e => fail(f(e)), a => pure(g(a)))
  }

  trait BIOMonad[F[+_, +_]] extends BIOApplicative[F] {
    def flatMap[E, A, E2 >: E, B](r: F[E, A])(f: A => F[E2, B]): F[E2, B]
    def flatten[E, A](r: F[E, F[E, A]]): F[E, A] = flatMap(r)(identity)

    // defaults
    @inline override def map[E, A, B](r: F[E, A])(f: A => B): F[E, B] = {
      flatMap(r)(a => pure(f(a)))
    }

    /** execute two operations in order, return result of second operation */
    @inline override def *>[E, A, E2 >: E, B](f: F[E, A], next: => F[E2, B]): F[E2, B] = {
      flatMap[E, A, E2, B](f)(_ => next)
    }

    /** execute two operations in order, same as `*>`, but return result of first operation */
    @inline override def <*[E, A, E2 >: E, B](f: F[E, A], next: => F[E2, B]): F[E2, A] = {
      flatMap[E, A, E2, A](f)(a => map(next)(_ => a))
    }

    /** execute two operations in order, map their results */
    @inline override def map2[E, A, E2 >: E, B, C](r1: F[E, A], r2: => F[E2, B])(f: (A, B) => C): F[E2, C] = {
      flatMap[E, A, E2, C](r1)(a => map(r2)(b => f(a, b)))
    }
  }

  trait BIOMonadError[F[+_, +_]] extends BIOError[F] with BIOMonad[F] {
    @inline def leftFlatMap[E, A, E2](r: F[E, A])(f: E => F[Nothing, E2]): F[E2, A] = {
      redeem(r)(e => flatMap(f(e))(fail(_)), pure)
    }
  }

  trait BIOBracket[F[+_, +_]] extends BIOMonadError[F] {
    def bracketCase[E, A, B](acquire: F[E, A])(release: (A, BIOExit[E, B]) => F[Nothing, Unit])(use: A => F[E, B]): F[E, B]

    @inline def bracket[E, A, B](acquire: F[E, A])(release: A => F[Nothing, Unit])(use: A => F[E, B]): F[E, B] = {
      bracketCase[E, A, B](acquire)((a, _) => release(a))(use)
    }

    // defaults
    @inline override def guarantee[E, A](f: F[E, A])(cleanup: F[Nothing, Unit]): F[E, A] = {
      bracket(unit)(_ => cleanup)(_ => f)
    }
  }

  trait BIOPanic[F[+_, +_]] extends BIOBracket[F] with BIOPanicSyntax {
    def terminate(v: => Throwable): F[Nothing, Nothing]
    def sandbox[E, A](r: F[E, A]): F[BIOExit.Failure[E], A]

    @inline final def orTerminate[A](r: F[Throwable, A]): F[Nothing, A] = catchAll(r)(terminate(_))
  }

  private[bio] sealed trait BIOPanicSyntax
  object BIOPanicSyntax {
    implicit final class BIOPanicOrTerminateK[F[+_, +_]](private val F: BIOPanic[F]) extends AnyVal {
      def orTerminateK: F[Throwable, ?] ~> F[Nothing, ?] = Lambda[F[Throwable, ?] ~> F[Nothing, ?]](F.orTerminate(_))
    }
  }

  trait BIO[F[+_, +_]] extends BIOPanic[F] {
    type Or[+E, +A] = F[E, A]
    type Just[+A] = F[Nothing, A]

    @inline def syncThrowable[A](effect: => A): F[Throwable, A]
    @inline def sync[A](effect: => A): F[Nothing, A]
    @inline final def apply[A](effect: => A): F[Throwable, A] = syncThrowable(effect)

    // defaults
    @inline override def fromEither[E, A](effect: => Either[E, A]): F[E, A] = flatMap(sync(effect)) {
      case Left(e) => fail(e): F[E, A]
      case Right(v) => pure(v): F[E, A]
    }

    @inline override def fromOption[E, A](errorOnNone: => E)(effect: => Option[A]): F[E, A] = {
      flatMap(sync(effect))(e => fromEither(e.toRight(errorOnNone)))
    }

    @inline override def fromTry[A](effect: => Try[A]): F[Throwable, A] = {
      syncThrowable(effect.get)
    }
  }

  trait BIOAsync[F[+_, +_]] extends BIO[F] with BIOAsyncInstances {
    final type Canceler = F[Nothing, Unit]

    @inline def async[E, A](register: (Either[E, A] => Unit) => Unit): F[E, A]
    @inline def asyncCancelable[E, A](register: (Either[E, A] => Unit) => Canceler): F[E, A]
    @inline def `yield`: F[Nothing, Unit]

    @inline def race[E, A](r1: F[E, A])(r2: F[E, A]): F[E, A]
    @inline def timeout[E, A](r: F[E, A])(duration: Duration): F[E, Option[A]]
    @inline def parTraverseN[E, A, B](maxConcurrent: Int)(l: Iterable[A])(f: A => F[E, B]): F[E, List[B]]

    @inline def sleep(duration: Duration): F[Nothing, Unit]

    @inline def uninterruptible[E, A](r: F[E, A]): F[E, A]

    @inline def retryOrElse[A, E, A2 >: A, E2](r: F[E, A])(duration: FiniteDuration, orElse: => F[E2, A2]): F[E2, A2]
    @inline final def repeatUntil[E, A](onTimeout: => E, sleep: FiniteDuration, maxAttempts: Int)(action: F[E, Option[A]]): F[E, A] = {
      def go(n: Int): F[E, A] = {
        flatMap(action) {
          case Some(value) =>
            pure(value)
          case None =>
            if (n <= maxAttempts) {
              *>(this.sleep(sleep), go(n + 1))
            } else {
              fail(onTimeout)
            }
        }
      }

      go(0)
    }
  }

  private[bio] sealed trait BIOAsyncInstances
  object BIOAsyncInstances {
    implicit def BIOAsyncZio[F](implicit clockService: zio.clock.Clock): BIOAsync[ZIO[F, +?, +?]] = new BIOAsyncZio[F](clockService)
  }

  trait BIOFork3[F[-_, +_, +_]] extends BIOForkInstances {
    def fork[R, E, A](f: F[R, E, A]): F[R, Nothing, BIOFiber[F[Any, +?, +?], E, A]]
  }

  private[bio] sealed trait BIOForkInstances
  object BIOForkInstances {
    implicit def BIOForkZioIO[R]: BIOFork[ZIO[R, +?, +?]] = BIOForkZio.asInstanceOf[BIOFork[ZIO[R, +?, +?]]]

    implicit object BIOForkZio extends BIOFork3[ZIO] {
      override def fork[R, E, A](f: ZIO[R, E, A]): ZIO[R, Nothing, BIOFiber[ZIO[Any, +?, +?], E, A]] =
        f.fork
          // FIXME: ZIO Bug / feature (interruption inheritance) breaks behavior in bracket/DIResource
          //  unless wrapped in `interruptible`
          //  see: https://github.com/zio/zio/issues/945
          .interruptible
          .map(BIOFiber.fromZIO)
    }
  }

  type BIOFork[F[+_, +_]] = BIOFork3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]

  type SyncSafe2[F[_, _]] = SyncSafe[F[Nothing, ?]]
  object SyncSafe2 {
    def apply[F[_, _] : SyncSafe2]: SyncSafe2[F] = implicitly
  }

  type Clock2[F[_, _]] = Clock[F[Nothing, ?]]
  object Clock2 {
    def apply[F[_, _]: Clock2]: Clock2[F] = implicitly
  }

  type Entropy2[F[_, _]] = Entropy[F[Nothing, ?]]
  object Entropy2 {
    def apply[F[_, _]: Entropy2]: Entropy2[F] = implicitly
  }
}

