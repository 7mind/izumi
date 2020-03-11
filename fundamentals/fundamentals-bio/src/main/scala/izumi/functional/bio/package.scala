package izumi.functional

import java.util.concurrent.CompletionStage

import cats.~>
import izumi.functional.bio.impl.{BIOTemporalZio, BIOZio}
import izumi.functional.bio.syntax.{BIO3Syntax, BIOSyntax}
import izumi.functional.mono.{Clock, Entropy, SyncSafe}
import zio.ZIO

import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions
import scala.util.Try

package object bio extends BIOSyntax with BIO3Syntax {

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
  @inline override final def FR[FR[-_, +_, +_]](implicit FR: BIOFunctor3[FR]): FR.type = FR

  /**
    * NOTE: The left type parameter is not forced to be covariant
    * because [[BIOFunctor]] does not yet expose any operations
    * on it.
    **/
  type BIOFunctor[F[_, +_]] = BIOFunctor3[Lambda[(`-R`, `E`, `+A`) => F[E, A]]]
  trait BIOFunctor3[F[-_, _, +_]] extends BIOFunctorInstances {
    def map[R, E, A, B](r: F[R, E, A])(f: A => B): F[R, E, B]

    def as[R, E, A, B](r: F[R, E, A])(v: => B): F[R, E, B] = map(r)(_ => v)
    def void[R, E, A](r: F[R, E, A]): F[R, E, Unit] = map(r)(_ => ())
    @inline final def widen[R, E, A, A1](r: F[R, E, A])(implicit @deprecated("unused", "") ev: A <:< A1): F[R, E, A1] = r.asInstanceOf[F[R, E, A1]]
  }

  private[bio] sealed trait BIOFunctorInstances
  object BIOFunctorInstances {
    // place ZIO instance at the root of the hierarchy, so that it's visible when summoning any class in hierarchy
    @inline implicit final val BIOZIO: BIOZio = BIOZio.asInstanceOf[BIOZio]

    @inline implicit final def AttachBIOPrimitives[F[+_, +_]](@deprecated("unused", "") self: BIOFunctor[F])(
      implicit BIOPrimitives: BIOPrimitives[F]
    ): BIOPrimitives.type = BIOPrimitives
    @inline implicit final def AttachBIOPrimitives3[F[-_, +_, +_]](@deprecated("unused", "") self: BIOFunctor3[F])(
      implicit BIOPrimitives: BIOPrimitives3[F]
    ): BIOPrimitives.type = BIOPrimitives

    @inline implicit final def AttachBIOFork[F[+_, +_]](@deprecated("unused", "") self: BIOFunctor[F])(implicit BIOFork: BIOFork[F]): BIOFork.type = BIOFork
    @inline implicit final def AttachBIOFork3[F[-_, +_, +_]](@deprecated("unused", "") self: BIOFunctor[F[Any, +?, +?]])(implicit BIOFork: BIOFork3[F]): BIOFork.type =
      BIOFork

    @inline implicit final def AttachBlockingIO[F[+_, +_]](@deprecated("unused", "") self: BIOFunctor[F])(implicit BlockingIO: BlockingIO[F]): BlockingIO.type =
      BlockingIO
    @inline implicit final def AttachBlockingIO3[F[-_, +_, +_]](@deprecated("unused", "") self: BIOFunctor[F[Any, +?, +?]])(
      implicit BlockingIO: BlockingIO3[F]
    ): BlockingIO.type = BlockingIO
  }

  type BIOBifunctor[F[+_, +_]] = BIOBifunctor3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  trait BIOBifunctor3[F[-_, +_, +_]] extends BIOFunctor3[F] {
    def bimap[R, E, A, E2, A2](r: F[R, E, A])(f: E => E2, g: A => A2): F[R, E2, A2]
    def leftMap[R, E, A, E2](r: F[R, E, A])(f: E => E2): F[R, E2, A] = bimap(r)(f, identity)

    @inline final def widenError[R, E, A, E1](r: F[R, E, A])(implicit @deprecated("unused", "") ev: E <:< E1): F[R, E1, A] = r.asInstanceOf[F[R, E1, A]]
    @inline final def widenBoth[R, E, A, E1, A1](r: F[R, E, A])(implicit @deprecated("unused", "") ev: E <:< E1, @deprecated("unused", "") ev2: A <:< A1): F[R, E1, A1] =
      r.asInstanceOf[F[R, E1, A1]]
  }

  type BIOApplicative[F[+_, +_]] = BIOApplicative3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  trait BIOApplicative3[F[-_, +_, +_]] extends BIOBifunctor3[F] {
    def pure[A](a: A): F[Any, Nothing, A]

    /** execute two operations in order, map their results */
    def map2[R, E, A, B, C](firstOp: F[R, E, A], secondOp: => F[R, E, B])(f: (A, B) => C): F[R, E, C]

    /** execute two operations in order, return result of second operation */
    def *>[R, E, A, B](firstOp: F[R, E, A], secondOp: => F[R, E, B]): F[R, E, B]

    /** execute two operations in order, same as `*>`, but return result of first operation */
    def <*[R, E, A, B](firstOp: F[R, E, A], secondOp: => F[R, E, B]): F[R, E, A]

    def traverse[R, E, A, B](l: Iterable[A])(f: A => F[R, E, B]): F[R, E, List[B]]

    @inline final def forever[R, E, A](r: F[R, E, A]): F[R, E, Nothing] = *>(r, forever(r))
    def traverse_[R, E, A](l: Iterable[A])(f: A => F[R, E, Unit]): F[R, E, Unit] = void(traverse(l)(f))
    def sequence[R, E, A, B](l: Iterable[F[R, E, A]]): F[R, E, List[A]] = traverse(l)(identity)
    def sequence_[R, E](l: Iterable[F[R, E, Unit]]): F[R, E, Unit] = void(traverse(l)(identity))

    final val unit: F[Any, Nothing, Unit] = pure(())
    @inline final def traverse[R, E, A, B](o: Option[A])(f: A => F[R, E, B]): F[R, E, Option[B]] = o match {
      case Some(a) => map(f(a))(Some(_))
      case None => pure(None)
    }
    @inline final def when[R, E](cond: Boolean)(ifTrue: F[R, E, Unit]): F[R, E, Unit] = if (cond) ifTrue else unit
    @inline final def unless[R, E](cond: Boolean)(ifFalse: F[R, E, Unit]): F[R, E, Unit] = if (cond) unit else ifFalse
    @inline final def ifThenElse[R, E, A](cond: Boolean)(ifTrue: F[R, E, A], ifFalse: F[R, E, A]): F[R, E, A] = if (cond) ifTrue else ifFalse
  }

  type BIOGuarantee[F[+_, +_]] = BIOGuarantee3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  trait BIOGuarantee3[F[-_, +_, +_]] extends BIOApplicative3[F] {
    def guarantee[R, E, A](f: F[R, E, A])(cleanup: F[R, Nothing, Unit]): F[R, E, A]
  }

  type BIOError[F[+_, +_]] = BIOError3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  trait BIOError3[F[-_, +_, +_]] extends BIOGuarantee3[F] {
    def fail[E](v: => E): F[Any, E, Nothing]
    def catchAll[R, E, A, E2, A2 >: A](r: F[R, E, A])(f: E => F[R, E2, A2]): F[R, E2, A2]
    def catchSome[R, E, A, E2 >: E, A2 >: A](r: F[R, E, A])(f: PartialFunction[E, F[R, E2, A2]]): F[R, E2, A2]

    def fromEither[E, V](effect: => Either[E, V]): F[Any, E, V]
    def fromOption[E, A](errorOnNone: => E)(effect: => Option[A]): F[Any, E, A]
    def fromTry[A](effect: => Try[A]): F[Any, Throwable, A]

    def redeemPure[R, E, A, B](r: F[R, E, A])(err: E => B, succ: A => B): F[R, Nothing, B] = catchAll(map(r)(succ))(e => pure(err(e)))
    def tapError[R, E, A, E1 >: E](r: F[R, E, A])(f: E => F[R, E1, Unit]): F[R, E1, A] = catchAll(r)(e => *>(f(e), fail(e)))
    def attempt[R, E, A](r: F[R, E, A]): F[R, Nothing, Either[E, A]] = redeemPure(r)(Left(_), Right(_))

    // defaults
    override def bimap[R, E, A, E2, B](r: F[R, E, A])(f: E => E2, g: A => B): F[R, E2, B] = catchAll(map(r)(g))(e => fail(f(e)))
  }

  type BIOMonad[F[+_, +_]] = BIOMonad3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  trait BIOMonad3[F[-_, +_, +_]] extends BIOApplicative3[F] {
    def flatMap[R, E, A, E2 >: E, B](r: F[R, E, A])(f: A => F[R, E2, B]): F[R, E2, B]
    def flatten[R, E, A](r: F[R, E, F[R, E, A]]): F[R, E, A] = flatMap(r)(identity)

    def tap[R, E, A, E2 >: E](r: F[R, E, A])(f: A => F[R, E2, Unit]): F[R, E2, A] = flatMap[R, E, A, E2, A](r)(a => as(f(a))(a))
    @inline final def when[R, E, E1](cond: F[R, E, Boolean])(ifTrue: F[R, E1, Unit])(implicit ev: E <:< E1): F[R, E1, Unit] = {
      ifThenElse(cond)(ifTrue, unit)
    }
    @inline final def unless[R, E, E1](cond: F[R, E, Boolean])(ifFalse: F[R, E1, Unit])(implicit ev: E <:< E1): F[R, E1, Unit] = {
      ifThenElse(cond)(unit, ifFalse)
    }
    @inline final def ifThenElse[R, E, E1, A](cond: F[R, E, Boolean])(ifTrue: F[R, E1, A], ifFalse: F[R, E1, A])(implicit ev: E <:< E1): F[R, E1, A] = {
      flatMap(widenError(cond)(ev))(if (_) ifTrue else ifFalse)
    }

    // defaults
    override def map[R, E, A, B](r: F[R, E, A])(f: A => B): F[R, E, B] = flatMap(r)(a => pure(f(a)))
    override def *>[R, E, A, B](f: F[R, E, A], next: => F[R, E, B]): F[R, E, B] = flatMap(f)(_ => next)
    override def <*[R, E, A, B](f: F[R, E, A], next: => F[R, E, B]): F[R, E, A] = flatMap(f)(a => map(next)(_ => a))
    override def map2[R, E, A, B, C](r1: F[R, E, A], r2: => F[R, E, B])(f: (A, B) => C): F[R, E, C] = flatMap(r1)(a => map(r2)(b => f(a, b)))
  }

  type BIOMonadError[F[+_, +_]] = BIOMonadError3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  trait BIOMonadError3[F[-_, +_, +_]] extends BIOError3[F] with BIOMonad3[F] {
    def redeem[R, E, A, E2, B](r: F[R, E, A])(err: E => F[R, E2, B], succ: A => F[R, E2, B]): F[R, E2, B] = {
      flatMap(attempt(r))(_.fold(err, succ))
    }

    def flip[R, E, A](r: F[R, E, A]): F[R, A, E] = {
      redeem(r)(pure, fail(_))
    }
    def leftFlatMap[R, E, A, E2](r: F[R, E, A])(f: E => F[R, Nothing, E2]): F[R, E2, A] = {
      redeem(r)(e => flatMap(f(e))(fail(_)), pure)
    }
    def tapBoth[R, E, A, E2 >: E](r: F[R, E, A])(err: E => F[R, E2, Unit], succ: A => F[R, E2, Unit]): F[R, E2, A] = {
      tap(tapError[R, E, A, E2](r)(err))(succ)
    }
    /** for-comprehensions sugar:
      *
      * {{{
      *   for {
      *    (1, 2) <- F.pure((2, 1))
      *   } yield ()
      * }}}
      */
    def withFilter[R, E, A](r: F[R, E, A])(predicate: A => Boolean)(implicit ev: NoSuchElementException <:< E): F[R, E, A] = {
      flatMap(r)(a => if (predicate(a)) pure(a) else fail(new NoSuchElementException("The value doesn't satisfy the predicate")))
    }
  }

  type BIOBracket[F[+_, +_]] = BIOBracket3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  trait BIOBracket3[F[-_, +_, +_]] extends BIOMonadError3[F] {
    def bracketCase[R, E, A, B](acquire: F[R, E, A])(release: (A, BIOExit[E, B]) => F[R, Nothing, Unit])(use: A => F[R, E, B]): F[R, E, B]

    def bracket[R, E, A, B](acquire: F[R, E, A])(release: A => F[R, Nothing, Unit])(use: A => F[R, E, B]): F[R, E, B] = {
      bracketCase[R, E, A, B](acquire)((a, _) => release(a))(use)
    }

    // defaults
    override def guarantee[R, E, A](f: F[R, E, A])(cleanup: F[R, Nothing, Unit]): F[R, E, A] = {
      bracket[R, E, Unit, A](unit)(_ => cleanup)(_ => f)
    }
  }

  type BIOPanic[F[+_, +_]] = BIOPanic3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  trait BIOPanic3[F[-_, +_, +_]] extends BIOBracket3[F] with BIOPanicSyntax {
    def terminate(v: => Throwable): F[Any, Nothing, Nothing]
    def sandbox[R, E, A](r: F[R, E, A]): F[R, BIOExit.Failure[E], A]

    @inline final def orTerminate[R, A](r: F[R, Throwable, A]): F[R, Nothing, A] = catchAll(r)(terminate(_))
  }

  private[bio] sealed trait BIOPanicSyntax
  object BIOPanicSyntax {
    implicit final class BIOPanicOrTerminateK[F[-_, +_, +_]](private val F: BIOPanic3[F]) extends AnyVal {
      def orTerminateK[R]: F[R, Throwable, ?] ~> F[R, Nothing, ?] = Lambda[F[R, Throwable, ?] ~> F[R, Nothing, ?]](f => F.orTerminate(f))
    }
  }

  type BIO[F[+_, +_]] = BIO3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  trait BIO3[F[-_, +_, +_]] extends BIOPanic3[F] {
    final type Or[+E, +A] = F[Any, E, A]
    final type Just[+A] = F[Any, Nothing, A]

    def syncThrowable[A](effect: => A): F[Any, Throwable, A]
    def sync[A](effect: => A): F[Any, Nothing, A]

    @inline final def apply[A](effect: => A): F[Any, Throwable, A] = syncThrowable(effect)

    def suspend[R, A](effect: => F[R, Throwable, A]): F[R, Throwable, A] = flatten(syncThrowable(effect))

    // defaults
    override def fromEither[E, A](effect: => Either[E, A]): F[Any, E, A] = flatMap(sync(effect)) {
      case Left(e) => fail(e): F[Any, E, A]
      case Right(v) => pure(v): F[Any, E, A]
    }
    override def fromOption[E, A](errorOnNone: => E)(effect: => Option[A]): F[Any, E, A] = {
      flatMap(sync(effect))(e => fromEither(e.toRight(errorOnNone)))
    }
    override def fromTry[A](effect: => Try[A]): F[Any, Throwable, A] = {
      syncThrowable(effect.get)
    }
  }

  type BIOAsync[F[+_, +_]] = BIOAsync3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  trait BIOAsync3[F[-_, +_, +_]] extends BIO3[F] {
    final type Canceler = F[Any, Nothing, Unit]

    def async[E, A](register: (Either[E, A] => Unit) => Unit): F[Any, E, A]
    def asyncF[R, E, A](register: (Either[E, A] => Unit) => F[R, E, Unit]): F[R, E, A]
    def asyncCancelable[E, A](register: (Either[E, A] => Unit) => Canceler): F[Any, E, A]

    def fromFuture[A](mkFuture: ExecutionContext => Future[A]): F[Any, Throwable, A]
    def fromFutureJava[A](javaFuture: => CompletionStage[A]): F[Any, Throwable, A]

    def yieldNow: F[Any, Nothing, Unit]

    /** Race two actions, the winner is the first action to TERMINATE, whether by success or failure */
    def race[R, E, A](r1: F[R, E, A], r2: F[R, E, A]): F[R, E, A]
    def racePair[R, E, A, B](fa: F[R, E, A], fb: F[R, E, B]): F[R, E, Either[(A, BIOFiber3[F[-?, +?, +?], R, E, B]), (BIOFiber3[F[-?, +?, +?], R, E, A], B)]]

    def parTraverseN[R, E, A, B](maxConcurrent: Int)(l: Iterable[A])(f: A => F[R, E, B]): F[R, E, List[B]]
    def parTraverse[R, E, A, B](l: Iterable[A])(f: A => F[R, E, B]): F[R, E, List[B]]

    def uninterruptible[R, E, A](r: F[R, E, A]): F[R, E, A]

    // defaults
    def never: F[Any, Nothing, Nothing] = async(_ => ())

    def parTraverse_[R, E, A, B](l: Iterable[A])(f: A => F[R, E, B]): F[R, E, Unit] = void(parTraverse(l)(f))
    def parTraverseN_[R, E, A, B](maxConcurrent: Int)(l: Iterable[A])(f: A => F[R, E, B]): F[R, E, Unit] = void(parTraverseN(maxConcurrent)(l)(f))

    @inline final def fromFuture[A](mkFuture: => Future[A]): F[Any, Throwable, A] = fromFuture(_ => mkFuture)
  }

  type BIOTemporal[F[+_, +_]] = BIOTemporal3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  trait BIOTemporal3[F[-_, +_, +_]] extends BIOAsync3[F] with BIOTemporalInstances {
    def sleep(duration: Duration): F[Any, Nothing, Unit]
    def timeout[R, E, A](r: F[R, E, A])(duration: Duration): F[R, E, Option[A]]
    def retryOrElse[R, A, E, A2 >: A, E2](r: F[R, E, A])(duration: FiniteDuration, orElse: => F[R, E2, A2]): F[R, E2, A2]

    @inline final def repeatUntil[R, E, A](action: F[R, E, Option[A]])(onTimeout: => E, sleep: FiniteDuration, maxAttempts: Int): F[R, E, A] = {
      def go(n: Int): F[R, E, A] = {
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
    implicit def BIOTemporalZio(implicit clockService: zio.clock.Clock): BIOTemporal3[ZIO[-?, +?, +?]] = new BIOTemporalZio(clockService)
  }

  type BIOFork[F[+_, +_]] = BIOFork3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  trait BIOFork3[F[-_, +_, +_]] extends BIOForkInstances {
    def fork[R, E, A](f: F[R, E, A]): F[R, Nothing, BIOFiber3[F[-?, +?, +?], R, E, A]]
  }

  private[bio] sealed trait BIOForkInstances
  object BIOForkInstances {
    // FIXME: bad encoding for lifting to 2-parameters...
    implicit def BIOForkZioIO[R]: BIOFork[ZIO[R, +?, +?]] = BIOForkZio.asInstanceOf[BIOFork[ZIO[R, +?, +?]]]

    implicit object BIOForkZio extends BIOFork3[ZIO] {
      override def fork[R, E, A](f: ZIO[R, E, A]): ZIO[R, Nothing, BIOFiber3[ZIO[-?, +?, +?], R, E, A]] =
        f
        // FIXME: ZIO Bug / feature (interruption inheritance) breaks behavior in bracket/DIResource
        //  unless wrapped in `interruptible`
        //  see: https://github.com/zio/zio/issues/945
        .interruptible.forkDaemon
          .map(BIOFiber.fromZIO)
    }
  }

  type BIOLatch[F[+_, +_]] = BIOPromise[F, Nothing, Unit]
  type BIOFiber[F[+_, +_], E, A] = BIOFiber3[Lambda[(`-R`, `E`, `+A`) => F[E, A]], Any, E, A]

  type BlockingIO[F[_, _]] = BlockingIO3[Lambda[(R, E, A) => F[E, A]]]
  object BlockingIO {
    def apply[F[_, _]: BlockingIO]: BlockingIO[F] = implicitly
  }

  type BIOPrimitives3[F[-_, +_, +_]] = BIOPrimitives[F[Any, +?, +?]]

  type SyncSafe2[F[_, _]] = SyncSafe[F[Nothing, ?]]
  object SyncSafe2 {
    def apply[F[_, _]: SyncSafe2]: SyncSafe2[F] = implicitly
  }
  type SyncSafe3[F[_, _, _]] = SyncSafe[F[Any, Nothing, ?]]
  object SyncSafe3 {
    def apply[F[_, _, _]: SyncSafe3]: SyncSafe3[F] = implicitly
  }

  type Clock2[F[_, _]] = Clock[F[Nothing, ?]]
  object Clock2 {
    def apply[F[_, _]: Clock2]: Clock2[F] = implicitly
  }
  type Clock3[F[_, _, _]] = Clock[F[Any, Nothing, ?]]
  object Clock3 {
    def apply[F[_, _, _]: Clock3]: Clock3[F] = implicitly
  }

  type Entropy2[F[_, _]] = Entropy[F[Nothing, ?]]
  object Entropy2 {
    def apply[F[_, _]: Entropy2]: Entropy2[F] = implicitly
  }
  type Entropy3[F[_, _, _]] = Entropy[F[Any, Nothing, ?]]
  object Entropy3 {
    def apply[F[_, _, _]: Entropy3]: Entropy3[F] = implicitly
  }
}
