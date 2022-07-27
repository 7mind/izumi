package izumi.functional.bio.impl

import cats.{ApplicativeError, Parallel}
import cats.effect.Outcome
import cats.effect.kernel.{Async, Fiber, Poll}
import izumi.functional.bio.data.{Morphism2, RestoreInterruption2}
import izumi.functional.bio.{Async2, BlockingIO2, Clock1, Clock2, Exit, Fiber2, Fork2, Primitives2, Promise2, Ref2, Semaphore2, Temporal2}

import java.time.{LocalDateTime, OffsetDateTime, ZonedDateTime}
import java.util.concurrent.CompletionStage
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.concurrent.{CancellationException, ExecutionContext, Future}
import scala.reflect.ClassTag

object CatsToBIO {

  type Bifunctorized[F[_], +E, +A]

  implicit def getClassTag[F[_], E, A]: ClassTag[Bifunctorized[F, E, A]] = implicitly[ClassTag[Any]].asInstanceOf[ClassTag[Bifunctorized[F, E, A]]]

  implicit final class CatsConversionsOps[F[_], E, A](private val target: Bifunctorized[F, E, A]) extends AnyVal {
    def unwrap: F[A] = target.asInstanceOf[F[A]]
  }

  object Bifunctorized {
    def assert[F[_], E, A](fa: F[A]): Bifunctorized[F, E, A] = fa.asInstanceOf[Bifunctorized[F, E, A]]

    def convertThrowable[F[_], A](f: F[A])(implicit F: ApplicativeError[F, Throwable]): Bifunctorized[F, Throwable, A] =
      Bifunctorized.assert(F.adaptError(f)(PrivateTypedError[Throwable]))
  }

  final case class PrivateTypedError[+E] private (error: E)
    extends RuntimeException(
      s"Typed error of class=${error.getClass.getName}: $error",
      error match { case t: Throwable => t; case _ => null },
    )
  object PrivateTypedError {
    def apply[E](error: E): PrivateTypedError[E] = error match {
      case PrivateTypedError(e) => this.apply(e.asInstanceOf[E])
      case e => new PrivateTypedError(e)
    }
  }

  def asyncToBIO[F[+_]](
    implicit F: Async[F]
  ): Async2[Bifunctorized[F, +_, +_]] &
  Temporal2[Bifunctorized[F, +_, +_]] &
  Fork2[Bifunctorized[F, +_, +_]] &
  BlockingIO2[Bifunctorized[F, +_, +_]] &
  Primitives2[Bifunctorized[F, +_, +_]] =
    new Async2[Bifunctorized[F, +_, +_]]
      with Temporal2[Bifunctorized[F, +_, +_]]
      with Fork2[Bifunctorized[F, +_, +_]]
      with BlockingIO2[Bifunctorized[F, +_, +_]]
      with Primitives2[Bifunctorized[F, +_, +_]] {

      private[this] implicit val P: Parallel[F] = cats.effect.instances.spawn.parallelForGenSpawn(F)

      private[this] def convertThrowable[A](f: F[A]): Bifunctorized[F, Throwable, A] = Bifunctorized.assert(F.adaptError(f)(PrivateTypedError[Throwable]))

      private[this] def outcomeToExit[E, A](outcome: Outcome[F, Throwable, A]): Bifunctorized[F, Nothing, Exit[E, A]] = outcome match {
        case Outcome.Succeeded(fa) => Bifunctorized.assert(F.map(fa)(Exit.Success(_)))
        case Outcome.Errored(exc @ PrivateTypedError(e)) => pure(Exit.Error(e.asInstanceOf[E], Exit.Trace.CatsTrace(exc)))
        case Outcome.Errored(t) => pure(Exit.Termination(t, Exit.Trace.CatsTrace(t)))
        case Outcome.Canceled() => pure(Exit.Interruption(Nil, Exit.Trace.empty))
      }

      private[this] def fromPoll(poll: Poll[F]): RestoreInterruption2[Bifunctorized[F, +_, +_]] = {
        Morphism2[Bifunctorized[F, +_, +_], Bifunctorized[F, +_, +_]](f => Bifunctorized.assert(poll(f.unwrap)))
      }

      private[this] def toFiber2[E, A](fiber: Fiber[F, Throwable, A]): Fiber2[Bifunctorized[F, +_, +_], E, A] = {
        new Fiber2[Bifunctorized[F, +_, +_], E, A] {
          override def join: Bifunctorized[F, E, A] =
            Bifunctorized.assert(fiber.joinWith(F.flatMap(F.canceled)(_ => F.raiseError(new CancellationException("The fiber was canceled")))))
          override def observe: Bifunctorized[F, Nothing, Exit[E, A]] = flatMap(Bifunctorized.assert(fiber.join))(outcomeToExit)
          override def interrupt: Bifunctorized[F, Nothing, Unit] = Bifunctorized.assert(fiber.cancel)
        }
      }

      override def async[E, A](register: (Either[E, A] => Unit) => Unit): Bifunctorized[F, E, A] = {
        Bifunctorized.assert(F.async[A](cb => F.as(F.delay(register(e => cb(e.left.map(PrivateTypedError[E])))), None)))
      }
      override def asyncF[R, E, A](register: (Either[E, A] => Unit) => Bifunctorized[F, E, Unit]): Bifunctorized[F, E, A] = {
        Bifunctorized.assert(F.async[A](cb => as(register(e => cb(e.left.map(PrivateTypedError[E]))))(None).unwrap))
      }
      override def asyncCancelable[E, A](register: (Either[E, A] => Unit) => Canceler): Bifunctorized[F, E, A] = {
        Bifunctorized.assert(F.async[A](cb => F.map[Canceler, Option[F[Unit]]](F.delay(register(e => cb(e.left.map(PrivateTypedError[E])))))(f => Some(f.unwrap))))
      }
      override def fromFuture[A](mkFuture: ExecutionContext => Future[A]): Bifunctorized[F, Throwable, A] = {
        convertThrowable(F.fromFuture(F.flatMap(F.executionContext)(ec => F.delay(mkFuture(ec)))))
      }
      override def currentEC: Bifunctorized[F, Nothing, ExecutionContext] = {
        Bifunctorized.assert(F.executionContext)
      }
      override def onEC[R, E, A](ec: ExecutionContext)(f: Bifunctorized[F, E, A]): Bifunctorized[F, E, A] = {
        Bifunctorized.assert(F.evalOn(f.unwrap, ec))
      }
      override def sleep(duration: Duration): Bifunctorized[F, Nothing, Unit] = {
        Bifunctorized.assert(duration match {
          case _: Duration.Infinite => F.never
          case finite: FiniteDuration => F.sleep(finite)
        })
      }
      override def timeout[R, E, A](duration: Duration)(r: Bifunctorized[F, E, A]): Bifunctorized[F, E, Option[A]] = {
        race(map(r)(Some(_)), as(sleep(duration))(None))
      }
      override def fork[R, E, A](f: Bifunctorized[F, E, A]): Bifunctorized[F, Nothing, Fiber2[Bifunctorized[F, +_, +_], E, A]] = {
        map(Bifunctorized.assert(F.start(f.unwrap)))(toFiber2)
      }
      override def forkOn[R, E, A](ec: ExecutionContext)(f: Bifunctorized[F, E, A]): Bifunctorized[F, Nothing, Fiber2[Bifunctorized[F, +_, +_], E, A]] = {
        map(Bifunctorized.assert(F.startOn(f.unwrap, ec)))(toFiber2)
      }

      override def syncBlocking[A](f: => A): Bifunctorized[F, Throwable, A] = {
        convertThrowable(F.blocking(f))
      }
      override def syncInterruptibleBlocking[A](f: => A): Bifunctorized[F, Throwable, A] = {
        convertThrowable(F.interruptible(f))
      }
      override def pure[A](a: A): Bifunctorized[F, Nothing, A] = {
        Bifunctorized.assert(F.pure(a))
      }
      override def terminate(v: => Throwable): Bifunctorized[F, Nothing, Nothing] = {
        Bifunctorized.assert(F.raiseError(v))
      }
      override def sandbox[R, E, A](r: Bifunctorized[F, E, A]): Bifunctorized[F, Exit.Failure[E], A] = {
        Bifunctorized.assert(
          F.handleErrorWith(r.unwrap) {
            case exc @ PrivateTypedError(e) => fail(Exit.Error(e.asInstanceOf[E], Exit.Trace.CatsTrace(exc))).unwrap
            case t => fail(Exit.Termination(t, Exit.Trace.CatsTrace(t))).unwrap
          }
        )
      }
      override def sendInterruptToSelf: Bifunctorized[F, Nothing, Unit] = {
        Bifunctorized.assert(F.canceled)
      }

      override def fail[E](v: => E): Bifunctorized[F, E, Nothing] = {
        Bifunctorized.assert(F.raiseError(PrivateTypedError(v)))
      }

      override def fromFutureJava[A](javaFuture: => CompletionStage[A]): Bifunctorized[F, Throwable, A] = {
        ???
      }
      override def clock: Clock2[Bifunctorized[F, +_, +_]] = {
        new Clock2[Bifunctorized[F, +_, +_]] {
          override def epoch: Bifunctorized[F, Nothing, Long] = map(Bifunctorized.assert(F.realTime))(_.toMillis)
          override def monotonicNano: Bifunctorized[F, Nothing, Long] = map(Bifunctorized.assert(F.monotonic))(_.toNanos)

          // ???
          override def now(accuracy: Clock1.ClockAccuracy): Bifunctorized[F, Nothing, ZonedDateTime] = sync(Clock1.Standard.now(accuracy))
          override def nowLocal(accuracy: Clock1.ClockAccuracy): Bifunctorized[F, Nothing, LocalDateTime] = sync(Clock1.Standard.nowLocal(accuracy))
          override def nowOffset(accuracy: Clock1.ClockAccuracy): Bifunctorized[F, Nothing, OffsetDateTime] = sync(Clock1.Standard.nowOffset(accuracy))
        }
      }
      override def shiftBlocking[R, E, A](f: Bifunctorized[F, E, A]): Bifunctorized[F, E, A] = {
        ???
      }
      override def mkRef[A](a: A): Bifunctorized[F, Nothing, Ref2[Bifunctorized[F, +_, +_], A]] = ???
      override def mkPromise[E, A]: Bifunctorized[F, Nothing, Promise2[Bifunctorized[F, +_, +_], E, A]] = ???
      override def mkSemaphore(permits: Long): Bifunctorized[F, Nothing, Semaphore2[Bifunctorized[F, +_, +_]]] = ???
      override def race[R, E, A](r1: Bifunctorized[F, E, A], r2: Bifunctorized[F, E, A]): Bifunctorized[F, E, A] = {
        ???
      }

      override def racePairUnsafe[R, E, A, B](fa: Bifunctorized[F, E, A], fb: Bifunctorized[F, E, B]): Bifunctorized[F, E, Either[
        (Exit[E, A], Fiber2[Bifunctorized[F, +_, +_], E, B]),
        (Fiber2[Bifunctorized[F, +_, +_], E, A], Exit[E, B]),
      ]] = {
        flatMap(Bifunctorized.assert(F.racePair(fa.unwrap, fb.unwrap))) {
          case Left((o, f)) => map(outcomeToExit[E, A](o))(e => Left((e, toFiber2[E, B](f))))
          case Right((f, o)) => map(outcomeToExit[E, B](o))(e => Right((toFiber2[E, A](f), e)))
        }
      }

      override def yieldNow: Bifunctorized[F, Nothing, Unit] = {
        Bifunctorized.assert(F.cede)
      }
      override def parTraverse[R, E, A, B](l: Iterable[A])(f: A => Bifunctorized[F, E, B]): Bifunctorized[F, E, List[B]] = {
        Bifunctorized.assert(Parallel.parTraverse(l.toList)(f.asInstanceOf[A => F[B]]))
      }
      override def parTraverseN[R, E, A, B](maxConcurrent: Int)(l: Iterable[A])(f: A => Bifunctorized[F, E, B]): Bifunctorized[F, E, List[B]] = {
        Bifunctorized.assert(F.parTraverseN(maxConcurrent)(l.toList)(f.asInstanceOf[A => F[B]]))
      }
      override def zipWithPar[R, E, A, B, C](fa: Bifunctorized[F, E, A], fb: Bifunctorized[F, E, B])(f: (A, B) => C): Bifunctorized[F, E, C] = {
        Bifunctorized.assert(Parallel.parMap2(fa.unwrap, fb.unwrap)(f))
      }

      override def bracketCase[R, E, A, B](
        acquire: Bifunctorized[F, E, A]
      )(release: (A, Exit[E, B]) => Bifunctorized[F, Nothing, Unit]
      )(use: A => Bifunctorized[F, E, B]
      ): Bifunctorized[F, E, B] = Bifunctorized.assert(F.bracketCase(acquire = acquire.unwrap)(use = use.asInstanceOf[A => F[B]])(release = {
        (a, outcome) => flatMap(outcomeToExit(outcome))(release(a, _)).unwrap
      }))

      override def uninterruptibleExcept[R, E, A](r: RestoreInterruption2[Bifunctorized[F, +_, +_]] => Bifunctorized[F, E, A]): Bifunctorized[F, E, A] = {
        Bifunctorized.assert(F.uncancelable(poll => r(fromPoll(poll)).unwrap))
      }

      override def bracketExcept[R, E, A, B](
        acquire: RestoreInterruption2[Bifunctorized[F, +_, +_]] => Bifunctorized[F, E, A]
      )(release: (A, Exit[E, B]) => Bifunctorized[F, Nothing, Unit]
      )(use: A => Bifunctorized[F, E, B]
      ): Bifunctorized[F, E, B] = {
        Bifunctorized.assert(
          F.bracketFull(acquire = poll => acquire(fromPoll(poll)).unwrap)(
            use = use.asInstanceOf[A => F[B]]
          )(release = (a, outcome) => flatMap(outcomeToExit(outcome))(release(a, _)).unwrap)
        )
      }

      override def syncThrowable[A](effect: => A): Bifunctorized[F, Throwable, A] = {
        convertThrowable(F.delay(effect))
      }
      override def sync[A](effect: => A): Bifunctorized[F, Nothing, A] = {
        Bifunctorized.assert(F.delay(effect))
      }
      override def catchAll[R, E, A, E2](r: Bifunctorized[F, E, A])(f: E => Bifunctorized[F, E2, A]): Bifunctorized[F, E2, A] = {
        Bifunctorized.assert(F.recoverWith(r.unwrap) {
          case PrivateTypedError(e) => f(e.asInstanceOf[E]).unwrap
        })
      }
      override def flatMap[R, E, A, B](r: Bifunctorized[F, E, A])(f: A => Bifunctorized[F, E, B]): Bifunctorized[F, E, B] = {
        Bifunctorized.assert(F.flatMap(r.unwrap)(f.asInstanceOf[A => F[B]]))
      }
      override def unit: Bifunctorized[F, Nothing, Unit] = {
        Bifunctorized.assert(F.unit)
      }

//      override def never: Bifunctorized[F, Nothing, Nothing] = super.never
//      override def mkLatch: Bifunctorized[F, Nothing, Promise2[Bifunctorized[F, +_, +_], Nothing, Unit]] = super.mkLatch
//
//      override def uninterruptible[R, E, A](r: Bifunctorized[F, E, A]): Bifunctorized[F, E, A] = super.uninterruptible(r)
//      override def parTraverse_[R, E, A, B](l: Iterable[A])(f: A => Bifunctorized[F, E, B]): Bifunctorized[F, E, Unit] = super.parTraverse_(l)(f)
//      override def parTraverseN_[R, E, A, B](maxConcurrent: Int)(l: Iterable[A])(f: A => Bifunctorized[F, E, B]): Bifunctorized[F, E, Unit] =
//        super.parTraverseN_(maxConcurrent)(l)(f)
//      /**
//        * Returns an effect that executes both effects,
//        * in parallel, combining their results into a tuple. If either side fails,
//        * then the other side will be interrupted.
//        */
//      override def zipPar[R, E, A, B](fa: Bifunctorized[F, E, A], fb: Bifunctorized[F, E, B]): Bifunctorized[F, E, (A, B)] = super.zipPar(fa, fb)
//      /**
//        * Returns an effect that executes both effects,
//        * in parallel, the left effect result is returned. If either side fails,
//        * then the other side will be interrupted.
//        */
//      override def zipParLeft[R, E, A, B](fa: Bifunctorized[F, E, A], fb: Bifunctorized[F, E, B]): Bifunctorized[F, E, A] = super.zipParLeft(fa, fb)
//      /**
//        * Returns an effect that executes both effects,
//        * in parallel, the right effect result is returned. If either side fails,
//        * then the other side will be interrupted.
//        */
//      override def zipParRight[R, E, A, B](fa: Bifunctorized[F, E, A], fb: Bifunctorized[F, E, B]): Bifunctorized[F, E, B] = super.zipParRight(fa, fb)
//      override def suspend[R, A](effect: => Bifunctorized[F, Throwable, A]): Bifunctorized[F, Throwable, A] = super.suspend(effect)
//      override def fromEither[E, A](effect: => Either[E, A]): Bifunctorized[F, E, A] = super.fromEither(effect)
//      override def fromOption[E, A](errorOnNone: => E)(effect: => Option[A]): Bifunctorized[F, E, A] = super.fromOption(errorOnNone)(effect)
//      override def fromTry[A](effect: => Try[A]): Bifunctorized[F, Throwable, A] = super.fromTry(effect)
//      override def bracket[R, E, A, B](
//        acquire: Bifunctorized[F, E, A]
//      )(release: A => Bifunctorized[F, Nothing, Unit]
//      )(use: A => Bifunctorized[F, E, B]
//      ): Bifunctorized[F, E, B] = super.bracket(acquire)(release)(use)
//      override def guaranteeCase[R, E, A](f: Bifunctorized[F, E, A], cleanup: Exit[E, A] => Bifunctorized[F, Nothing, Unit]): Bifunctorized[F, E, A] =
//        super.guaranteeCase(f, cleanup)
//      override def guarantee[R, E, A](f: Bifunctorized[F, E, A], cleanup: Bifunctorized[F, Nothing, Unit]): Bifunctorized[F, E, A] = super.guarantee(f, cleanup)
//      override def catchSome[R, E, A, E1 >: E](r: Bifunctorized[F, E, A])(f: PartialFunction[E, Bifunctorized[F, E1, A]]): Bifunctorized[F, E1, A] =
//        super.catchSome[R, E, A, E1](r)(f)
//      override def redeem[R, E, A, E2, B](r: Bifunctorized[F, E, A])(err: E => Bifunctorized[F, E2, B], succ: A => Bifunctorized[F, E2, B]): Bifunctorized[F, E2, B] =
//        super.redeem(r)(err, succ)
//      override def redeemPure[R, E, A, B](r: Bifunctorized[F, E, A])(err: E => B, succ: A => B): Bifunctorized[F, Nothing, B] = super.redeemPure(r)(err, succ)
//      override def attempt[R, E, A](r: Bifunctorized[F, E, A]): Bifunctorized[F, Nothing, Either[E, A]] = super.attempt(r)
//      override def tapError[R, E, A, E1 >: E](r: Bifunctorized[F, E, A])(f: E => Bifunctorized[F, E1, Unit]): Bifunctorized[F, E1, A] = super.tapError[R, E, A, E1](r)(f)
//      override def flip[R, E, A](r: Bifunctorized[F, E, A]): Bifunctorized[F, A, E] = super.flip(r)
//      override def leftFlatMap[R, E, A, E2](r: Bifunctorized[F, E, A])(f: E => Bifunctorized[F, Nothing, E2]): Bifunctorized[F, E2, A] =
//        super.leftFlatMap[R, E, A, E2](r)(f)
//      override def tapBoth[R, E, A, E1 >: E](
//        r: Bifunctorized[F, E, A]
//      )(err: E => Bifunctorized[F, E1, Unit],
//        succ: A => Bifunctorized[F, E1, Unit],
//      ): Bifunctorized[F, E1, A] = super.tapBoth[R, E, A, E1](r)(err, succ)
//      /** Extracts the optional value or fails with the `errorOnNone` error */
//      override def fromOption[R, E, A](errorOnNone: => E, r: Bifunctorized[F, E, Option[A]]): Bifunctorized[F, E, A] = super.fromOption(errorOnNone, r)
//      /** Retries this effect while its error satisfies the specified predicate. */
//      override def retryWhile[R, E, A](r: Bifunctorized[F, E, A])(f: E => Boolean): Bifunctorized[F, E, A] = super.retryWhile(r)(f)
//      /** Retries this effect while its error satisfies the specified effectful predicate. */
//      override def retryWhileF[R, R1 <: R, E, A](r: Bifunctorized[F, E, A])(f: E => Bifunctorized[F, Nothing, Boolean]): Bifunctorized[F, E, A] = super.retryWhileF(r)(f)
//      /** Retries this effect until its error satisfies the specified predicate. */
//      override def retryUntil[R, E, A](r: Bifunctorized[F, E, A])(f: E => Boolean): Bifunctorized[F, E, A] = super.retryUntil(r)(f)
//      /** Retries this effect until its error satisfies the specified effectful predicate. */
//      override def retryUntilF[R, R1 <: R, E, A](r: Bifunctorized[F, E, A])(f: E => Bifunctorized[F, Nothing, Boolean]): Bifunctorized[F, E, A] = super.retryUntilF(r)(f)
//      override def bimap[R, E, A, E2, B](r: Bifunctorized[F, E, A])(f: E => E2, g: A => B): Bifunctorized[F, E2, B] = super.bimap(r)(f, g)
//      override def leftMap2[R, E, A, E2, E3](firstOp: Bifunctorized[F, E, A], secondOp: => Bifunctorized[F, E2, A])(f: (E, E2) => E3): Bifunctorized[F, E3, A] =
//        super.leftMap2(firstOp, secondOp)(f)
//      override def orElse[R, E, A, E2](r: Bifunctorized[F, E, A], f: => Bifunctorized[F, E2, A]): Bifunctorized[F, E2, A] = super.orElse(r, f)
//      override def flatten[R, E, A](r: Bifunctorized[F, E, Bifunctorized[F, E, A]]): Bifunctorized[F, E, A] = super.flatten(r)
//      override def tailRecM[R, E, A, B](a: A)(f: A => Bifunctorized[F, E, Either[A, B]]): Bifunctorized[F, E, B] = super.tailRecM(a)(f)
//      override def tap[R, E, A](r: Bifunctorized[F, E, A], f: A => Bifunctorized[F, E, Unit]): Bifunctorized[F, E, A] = super.tap(r, f)
//      /** Extracts the optional value, or executes the `fallbackOnNone` effect */
//      override def fromOptionF[R, E, A](fallbackOnNone: => Bifunctorized[F, E, A], r: Bifunctorized[F, E, Option[A]]): Bifunctorized[F, E, A] =
//        super.fromOptionF(fallbackOnNone, r)
//      /**
//        * Execute an action repeatedly until its result fails to satisfy the given predicate
//        * and return that result, discarding all others.
//        */
//      override def iterateWhile[R, E, A](r: Bifunctorized[F, E, A])(p: A => Boolean): Bifunctorized[F, E, A] = super.iterateWhile(r)(p)
//      /**
//        * Execute an action repeatedly until its result satisfies the given predicate
//        * and return that result, discarding all others.
//        */
//      override def iterateUntil[R, E, A](r: Bifunctorized[F, E, A])(p: A => Boolean): Bifunctorized[F, E, A] = super.iterateUntil(r)(p)
//      /**
//        * Apply an effectful function iteratively until its result fails
//        * to satisfy the given predicate and return that result.
//        */
//      override def iterateWhileF[R, E, A](init: A)(f: A => Bifunctorized[F, E, A])(p: A => Boolean): Bifunctorized[F, E, A] = super.iterateWhileF(init)(f)(p)
//      /**
//        * Apply an effectful function iteratively until its result satisfies
//        * the given predicate and return that result.
//        */
//      override def iterateUntilF[R, E, A](init: A)(f: A => Bifunctorized[F, E, A])(p: A => Boolean): Bifunctorized[F, E, A] = super.iterateUntilF(init)(f)(p)
//      override def map[R, E, A, B](r: Bifunctorized[F, E, A])(f: A => B): Bifunctorized[F, E, B] = super.map(r)(f)
//      override def *>[R, E, A, B](f: Bifunctorized[F, E, A], next: => Bifunctorized[F, E, B]): Bifunctorized[F, E, B] = super.*>(f, next)
//      override def <*[R, E, A, B](f: Bifunctorized[F, E, A], next: => Bifunctorized[F, E, B]): Bifunctorized[F, E, A] = super.<*(f, next)
//      override def map2[R, E, A, B, C](r1: Bifunctorized[F, E, A], r2: => Bifunctorized[F, E, B])(f: (A, B) => C): Bifunctorized[F, E, C] = super.map2(r1, r2)(f)
//      override def as[R, E, A, B](r: Bifunctorized[F, E, A])(v: => B): Bifunctorized[F, E, B] = super.as(r)(v)
//      override def void[R, E, A](r: Bifunctorized[F, E, A]): Bifunctorized[F, E, Unit] = super.void(r)
//      /** Extracts the optional value, or returns the given `valueOnNone` value */
//      override def fromOptionOr[R, E, A](valueOnNone: => A, r: Bifunctorized[F, E, Option[A]]): Bifunctorized[F, E, A] = super.fromOptionOr(valueOnNone, r)
//      override def leftMap[R, E, A, E2](r: Bifunctorized[F, E, A])(f: E => E2): Bifunctorized[F, E2, A] = super.leftMap(r)(f)
//      override def traverse[R, E, A, B](l: Iterable[A])(f: A => Bifunctorized[F, E, B]): Bifunctorized[F, E, List[B]] = super.traverse(l)(f)
//      override def traverse_[R, E, A](l: Iterable[A])(f: A => Bifunctorized[F, E, Unit]): Bifunctorized[F, E, Unit] = super.traverse_(l)(f)
//      override def sequence[R, E, A, B](l: Iterable[Bifunctorized[F, E, A]]): Bifunctorized[F, E, List[A]] = super.sequence(l)
//      override def sequence_[R, E](l: Iterable[Bifunctorized[F, E, Unit]]): Bifunctorized[F, E, Unit] = super.sequence_(l)
    }
}
