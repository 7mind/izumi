package izumi.functional

import java.util.concurrent.CompletionStage

import cats.~>
import izumi.functional.bio.impl.{BIOTemporalZio, BIOZio}
import izumi.functional.mono.{Clock, Entropy, SyncSafe}
import zio.ZIO

import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions
import scala.util.Try

package object bio extends BIOSyntax {

  /**
    * A convenient dependent summoner for BIO* hierarchy.
    * Auto-narrows to the most powerful available class:
    *
    * {{{
    *   def y[F[+_, +_]: BIOTemporal] = {
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
    **/
  trait BIOFunctor[F[_, +_]] extends BIOFunctorInstances {
    def map[E, A, B](r: F[E, A])(f: A => B): F[E, B]

    def as[E, A, B](r: F[E, A])(v: => B): F[E, B] = map(r)(_ => v)
    def void[E, A](r: F[E, A]): F[E, Unit] = map(r)(_ => ())
    @inline final def widen[E, A, A1](r: F[E, A])(implicit @deprecated("unused", "") ev: A <:< A1): F[E, A1] = r.asInstanceOf[F[E, A1]]
  }

  private[bio] sealed trait BIOFunctorInstances
  object BIOFunctorInstances {
    // place ZIO instance at the root of the hierarchy, so that it's visible when summoning any class in hierarchy
    @inline implicit final def BIOZIO[R]: BIOZio[R] = BIOZio.asInstanceOf[BIOZio[R]]

    @inline implicit final def AttachBIOPrimitives[F[+_, +_]](@deprecated("unused", "") self: BIOFunctor[F])(implicit BIOPrimitives: BIOPrimitives[F]): BIOPrimitives.type = BIOPrimitives

    @inline implicit final def AttachBIOFork[F[+_, +_]](@deprecated("unused", "") self: BIOFunctor[F])(implicit BIOFork: BIOFork[F]): BIOFork.type = BIOFork
    @inline implicit final def AttachBIOFork3[F[-_, +_, +_]](@deprecated("unused", "") self: BIOFunctor[F[Any, +?, +?]])(implicit BIOFork: BIOFork3[F]): BIOFork.type = BIOFork

    @inline implicit final def AttachBlockingIO[F[+_, +_]](@deprecated("unused", "") self: BIOFunctor[F])(implicit BlockingIO: BlockingIO[F]): BlockingIO.type = BlockingIO
    @inline implicit final def AttachBlockingIO3[F[-_, +_, +_]](@deprecated("unused", "") self: BIOFunctor[F[Any, +?, +?]])(implicit BlockingIO: BlockingIO3[F]): BlockingIO.type = BlockingIO
  }

  trait BIOBifunctor[F[+_, +_]] extends BIOFunctor[F] {
    def bimap[E, A, E2, A2](r: F[E, A])(f: E => E2, g: A => A2): F[E2, A2]
    def leftMap[E, A, E2](r: F[E, A])(f: E => E2): F[E2, A] = bimap(r)(f, identity)

    @inline final def widenError[E, A, E1](r: F[E, A])(implicit @deprecated("unused","") ev: E <:< E1): F[E1, A] = r.asInstanceOf[F[E1, A]]
    @inline final def widenBoth[E, A, E1, A1](r: F[E, A])(implicit @deprecated("unused","") ev: E <:< E1, @deprecated("unused", "") ev2: A <:< A1): F[E1, A1] = r.asInstanceOf[F[E1, A1]]
  }

  trait BIOApplicative[F[+_, +_]] extends BIOBifunctor[F] {
    def pure[A](a: A): F[Nothing, A]

    /** execute two operations in order, map their results */
    def map2[E, A, B, C](firstOp: F[E, A], secondOp: => F[E, B])(f: (A, B) => C): F[E, C]

    /** execute two operations in order, return result of second operation */
    def *>[E, A, B](firstOp: F[E, A], secondOp: => F[E, B]): F[E, B]

    /** execute two operations in order, same as `*>`, but return result of first operation */
    def <*[E, A, B](firstOp: F[E, A], secondOp: => F[E, B]): F[E, A]

    def traverse[E, A, B](l: Iterable[A])(f: A => F[E, B]): F[E, List[B]]

    @inline final def forever[E, A](r: F[E, A]): F[E, Nothing] = *>(r, forever(r))
    def traverse_[E, A](l: Iterable[A])(f: A => F[E, Unit]): F[E, Unit] = void(traverse(l)(f))
    def sequence[E, A, B](l: Iterable[F[E, A]]): F[E, List[A]] = traverse(l)(identity)
    def sequence_[E](l: Iterable[F[E, Unit]]): F[E, Unit] = void(traverse(l)(identity))

    final val unit: F[Nothing, Unit] = pure(())
    @inline final def traverse[E, A, B](o: Option[A])(f: A => F[E, B]): F[E, Option[B]] = o match {
      case Some(a) => map(f(a))(Some(_))
      case None => pure(None)
    }
    @inline final def when[E](cond: Boolean)(ifTrue: F[E, Unit]): F[E, Unit] = if (cond) ifTrue else unit
    @inline final def unless[E](cond: Boolean)(ifFalse: F[E, Unit]): F[E, Unit] = if (cond) unit else ifFalse
    @inline final def ifThenElse[E, A](cond: Boolean)(ifTrue: F[E, A], ifFalse: F[E, A]): F[E, A] = if (cond) ifTrue else ifFalse
  }

  trait BIOGuarantee[F[+_, +_]] extends BIOApplicative[F] {
    def guarantee[E, A](f: F[E, A])(cleanup: F[Nothing, Unit]): F[E, A]
  }

  trait BIOError[F[+_, +_]] extends BIOGuarantee[F] {
    def fail[E](v: => E): F[E, Nothing]
    def catchAll[E, A, E2, A2 >: A](r: F[E, A])(f: E => F[E2, A2]): F[E2, A2]
    def catchSome[E, A, E2 >: E, A2 >: A](r: F[E, A])(f: PartialFunction[E, F[E2, A2]]): F[E2, A2]

    def fromEither[E, V](effect: => Either[E, V]): F[E, V]
    def fromOption[E, A](errorOnNone: => E)(effect: => Option[A]): F[E, A]
    def fromTry[A](effect: => Try[A]): F[Throwable, A]

    def redeemPure[E, A, B](r: F[E, A])(err: E => B, succ: A => B): F[Nothing, B] = catchAll(map(r)(succ))(e => pure(err(e)))
    def tapError[E, A, E1 >: E](r: F[E, A])(f: E => F[E1, Unit]): F[E1, A] = catchAll(r)(e => *>(f(e), fail(e)))
    def attempt[E, A](r: F[E, A]): F[Nothing, Either[E, A]] = redeemPure(r)(Left(_), Right(_))

    // defaults
    override def bimap[E, A, E2, B](r: F[E, A])(f: E => E2, g: A => B): F[E2, B] = catchAll(map(r)(g))(e => fail(f(e)))
  }

  trait BIOMonad[F[+_, +_]] extends BIOApplicative[F] {
    def flatMap[E, A, E2 >: E, B](r: F[E, A])(f: A => F[E2, B]): F[E2, B]
    def flatten[E, A](r: F[E, F[E, A]]): F[E, A] = flatMap(r)(identity)

    def tap[E, A, E2 >: E](r: F[E, A])(f: A => F[E2, Unit]): F[E2, A] = flatMap[E, A, E2, A](r)(a => as(f(a))(a))
    @inline final def when[E, E1](cond: F[E, Boolean])(ifTrue: F[E1, Unit])(implicit ev: E <:< E1): F[E1, Unit] = {
      ifThenElse(cond)(ifTrue, unit)
    }
    @inline final def unless[E, E1](cond: F[E, Boolean])(ifFalse: F[E1, Unit])(implicit ev: E <:< E1): F[E1, Unit] = {
      ifThenElse(cond)(unit, ifFalse)
    }
    @inline final def ifThenElse[E, E1, A](cond: F[E, Boolean])(ifTrue: F[E1, A], ifFalse: F[E1, A])(implicit ev: E <:< E1): F[E1, A] = {
      flatMap(widenError(cond)(ev))(if (_) ifTrue else ifFalse)
    }

    // defaults
    override def map[E, A, B](r: F[E, A])(f: A => B): F[E, B] = flatMap(r)(a => pure(f(a)))
    override def *>[E, A, B](f: F[E, A], next: => F[E, B]): F[E, B] = flatMap(f)(_ => next)
    override def <*[E, A, B](f: F[E, A], next: => F[E, B]): F[E, A] = flatMap(f)(a => map(next)(_ => a))
    override def map2[E, A, B, C](r1: F[E, A], r2: => F[E, B])(f: (A, B) => C): F[E, C] = flatMap(r1)(a => map(r2)(b => f(a, b)))
  }

  trait BIOMonadError[F[+_, +_]] extends BIOError[F] with BIOMonad[F] {
    def redeem[E, A, E2, B](r: F[E, A])(err: E => F[E2, B], succ: A => F[E2, B]): F[E2, B] = {
      flatMap(attempt(r))(_.fold(err, succ))
    }

    def flip[E, A](r: F[E, A]): F[A, E] = {
      redeem(r)(pure, fail(_))
    }
    def leftFlatMap[E, A, E2](r: F[E, A])(f: E => F[Nothing, E2]): F[E2, A] = {
      redeem(r)(e => flatMap(f(e))(fail(_)), pure)
    }
    def tapBoth[E, A, E2 >: E](r: F[E, A])(err: E => F[E2, Unit], succ: A => F[E2, Unit]): F[E2, A] = {
      tap(tapError[E, A, E2](r)(err))(succ)
    }
    /** for-comprehensions sugar:
      *
      * {{{
      *   for {
      *    (1, 2) <- F.pure((2, 1))
      *   } yield ()
      * }}}
      */
    def withFilter[E, A](r: F[E, A])(predicate: A => Boolean)(implicit ev: NoSuchElementException <:< E): F[E, A] = {
      flatMap(r)(a => if (predicate(a)) pure(a) else fail(new NoSuchElementException("The value doesn't satisfy the predicate")))
    }
  }

  trait BIOBracket[F[+_, +_]] extends BIOMonadError[F] {
    def bracketCase[E, A, B](acquire: F[E, A])(release: (A, BIOExit[E, B]) => F[Nothing, Unit])(use: A => F[E, B]): F[E, B]

    def bracket[E, A, B](acquire: F[E, A])(release: A => F[Nothing, Unit])(use: A => F[E, B]): F[E, B] = {
      bracketCase[E, A, B](acquire)((a, _) => release(a))(use)
    }

    // defaults
    override def guarantee[E, A](f: F[E, A])(cleanup: F[Nothing, Unit]): F[E, A] = {
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
    final type Or[+E, +A] = F[E, A]
    final type Just[+A] = F[Nothing, A]

    def syncThrowable[A](effect: => A): F[Throwable, A]
    def sync[A](effect: => A): F[Nothing, A]

    @inline final def apply[A](effect: => A): F[Throwable, A] = syncThrowable(effect)

    def suspend[A](effect: => F[Throwable, A]): F[Throwable, A] = flatten(syncThrowable(effect))

    // defaults
    override def fromEither[E, A](effect: => Either[E, A]): F[E, A] = flatMap(sync(effect)) {
      case Left(e) => fail(e): F[E, A]
      case Right(v) => pure(v): F[E, A]
    }
    override def fromOption[E, A](errorOnNone: => E)(effect: => Option[A]): F[E, A] = {
      flatMap(sync(effect))(e => fromEither(e.toRight(errorOnNone)))
    }
    override def fromTry[A](effect: => Try[A]): F[Throwable, A] = {
      syncThrowable(effect.get)
    }
  }

  trait BIOAsync[F[+_, +_]] extends BIO[F] {
    final type Canceler = F[Nothing, Unit]

    def async[E, A](register: (Either[E, A] => Unit) => Unit): F[E, A]
    def asyncF[E, A](register: (Either[E, A] => Unit) => F[E, Unit]): F[E, A]
    def asyncCancelable[E, A](register: (Either[E, A] => Unit) => Canceler): F[E, A]

    def fromFuture[A](mkFuture: ExecutionContext => Future[A]): F[Throwable, A]
    def fromFutureJava[A](javaFuture: => CompletionStage[A]): F[Throwable, A]

    def yieldNow: F[Nothing, Unit]

    /** Race two actions, the winner is the first action to TERMINATE, whether by success or failure */
    def race[E, A](r1: F[E, A], r2: F[E, A]): F[E, A]
    def racePair[E, A, B](fa: F[E, A], fb: F[E, B]): F[E, Either[(A, BIOFiber[F, E, B]), (BIOFiber[F, E, A], B)]]

    def parTraverseN[E, A, B](maxConcurrent: Int)(l: Iterable[A])(f: A => F[E, B]): F[E, List[B]]
    def parTraverse[E, A, B](l: Iterable[A])(f: A => F[E, B]): F[E, List[B]]

    def uninterruptible[E, A](r: F[E, A]): F[E, A]

    // defaults
    def never: F[Nothing, Nothing] = async(_ => ())

    def parTraverse_[E, A, B](l: Iterable[A])(f: A => F[E, B]): F[E, Unit] = void(parTraverse(l)(f))
    def parTraverseN_[E, A, B](maxConcurrent: Int)(l: Iterable[A])(f: A => F[E, B]): F[E, Unit] = void(parTraverseN(maxConcurrent)(l)(f))

    @inline final def fromFuture[A](mkFuture: => Future[A]): F[Throwable, A] = fromFuture(_ => mkFuture)
  }

  trait BIOTemporal[F[+_, +_]] extends BIOAsync[F] with BIOTemporalInstances {
    def sleep(duration: Duration): F[Nothing, Unit]
    def timeout[E, A](r: F[E, A])(duration: Duration): F[E, Option[A]]
    def retryOrElse[A, E, A2 >: A, E2](r: F[E, A])(duration: FiniteDuration, orElse: => F[E2, A2]): F[E2, A2]

    @inline final def repeatUntil[E, A](action: F[E, Option[A]])(onTimeout: => E, sleep: FiniteDuration, maxAttempts: Int): F[E, A] = {
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

  private[bio] sealed trait BIOTemporalInstances
  object BIOTemporalInstances {
    implicit def BIOTemporalZio[R](implicit clockService: zio.clock.Clock): BIOTemporal[ZIO[R, +?, +?]] = new BIOTemporalZio[R](clockService)
  }

  trait BIOFork3[F[-_, +_, +_]] extends BIOForkInstances {
    def fork[R, E, A](f: F[R, E, A]): F[R, Nothing, BIOFiber[F[Any, +?, +?], E, A]]
  }

  private[bio] sealed trait BIOForkInstances
  object BIOForkInstances {
    // FIXME: bad encoding for lifting to 2-parameters...
    implicit def BIOForkZioIO[R]: BIOFork[ZIO[R, +?, +?]] = BIOForkZio.asInstanceOf[BIOFork[ZIO[R, +?, +?]]]

    implicit object BIOForkZio extends BIOFork3[ZIO] {
      override def fork[R, E, A](f: ZIO[R, E, A]): ZIO[R, Nothing, BIOFiber[ZIO[Any, +?, +?], E, A]] =
        f
          // FIXME: ZIO Bug / feature (interruption inheritance) breaks behavior in bracket/DIResource
          //  unless wrapped in `interruptible`
          //  see: https://github.com/zio/zio/issues/945
          .interruptible
          .forkDaemon
          .map(BIOFiber.fromZIO)
    }
  }

  type BIOLatch[F[+_, +_]] = BIOPromise[F, Nothing, Unit]

  type BIOFork[F[+_, +_]] = BIOFork3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  type BlockingIO[F[_, _]] = BlockingIO3[Lambda[(R, E, A) => F[E, A]]]
  object BlockingIO {
    def apply[F[_, _]: BlockingIO]: BlockingIO[F] = implicitly
  }

  type BIOPrimitives3[F[-_, +_, +_]] = BIOPrimitives[F[Any, +?, +?]]

  type SyncSafe2[F[_, _]] = SyncSafe[F[Nothing, ?]]
  object SyncSafe2 {
    def apply[F[_, _]: SyncSafe2]: SyncSafe2[F] = implicitly
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
