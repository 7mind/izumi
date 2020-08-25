package izumi.functional.bio.impl

import java.util.concurrent.CompletionStage

import izumi.functional.bio.BIOExit.MonixExit._
import izumi.functional.bio.{BIOAsync, BIOExit, BIOFiber}
import monix.bio.{Cause, IO, Task}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

object BIOAsyncMonix extends BIOAsyncMonix

class BIOAsyncMonix extends BIOAsync[IO] {
  override final def pure[A](a: A): IO[Nothing, A] = IO.pure(a)
  override final def sync[A](effect: => A): IO[Nothing, A] = IO.evalTotal(effect)
  override final def syncThrowable[A](effect: => A): IO[Throwable, A] = IO.eval(effect)
  override final def suspend[R, A](effect: => IO[Throwable, A]): IO[Throwable, A] = IO.suspendTotal(effect)

  override final def fail[E](v: => E): IO[E, Nothing] = IO.raiseError(v)
  override final def terminate(v: => Throwable): IO[Nothing, Nothing] = IO.terminate(v)

  override final def fromEither[E, V](effect: => Either[E, V]): IO[E, V] = IO.fromEither(effect)
  override final def fromOption[E, A](errorOnNone: => E)(effect: => Option[A]): IO[E, A] = IO.fromEither(effect.toRight(errorOnNone))
  override final def fromTry[A](effect: => Try[A]): IO[Throwable, A] = IO.fromTry(effect)

  override final def void[R, E, A](r: IO[E, A]): IO[E, Unit] = r.void
  override final def map[R, E, A, B](r: IO[E, A])(f: A => B): IO[E, B] = r.map(f)
  override final def as[R, E, A, B](r: IO[E, A])(v: => B): IO[E, B] = r.map(_ => v)

  override final def tapError[R, E, A, E1 >: E](r: IO[E, A])(f: E => IO[E1, Unit]): IO[E1, A] = r.tapError(f)
  override final def leftMap[R, E, A, E2](r: IO[E, A])(f: E => E2): IO[E2, A] = r.mapError(f)
  override final def leftFlatMap[R, E, A, E2](r: IO[E, A])(f: E => IO[Nothing, E2]): IO[E2, A] = r.flipWith(_ flatMap f)
  override final def flip[R, E, A](r: IO[E, A]): IO[A, E] = r.flip
  override final def bimap[R, E, A, E2, B](r: IO[E, A])(f: E => E2, g: A => B): IO[E2, B] = r.bimap(f, g)

  override final def flatMap[R, E, A, B](r: IO[E, A])(f: A => IO[E, B]): IO[E, B] = r.flatMap(f)
  override final def tap[R, E, A](r: IO[E, A])(f: A => IO[E, Unit]): IO[E, A] = r.flatMap(a => f(a).map(_ => a))

  override final def tapBoth[R, E, A, E1 >: E](r: IO[E, A])(err: E => IO[E1, Unit], succ: A => IO[E1, Unit]): IO[E1, A] =
    r.tapError(err).flatMap(a => succ(a).map(_ => a))

  override final def flatten[R, E, A](r: IO[E, IO[E, A]]): IO[E, A] = r.flatten
  override final def *>[R, E, A, B](r: IO[E, A], next: => IO[E, B]): IO[E, B] = r.flatMap(_ => next)
  override final def <*[R, E, A, B](r: IO[E, A], next: => IO[E, B]): IO[E, A] = r.flatMap(a => next.map(_ => a))
  override final def map2[R, E, A, B, C](r1: IO[E, A], r2: => IO[E, B])(f: (A, B) => C): IO[E, C] = r1.flatMap(a => r2.map(f(a, _)))

  override final def redeem[R, E, A, E2, B](r: IO[E, A])(err: E => IO[E2, B], succ: A => IO[E2, B]): IO[E2, B] = r.redeemWith(err, succ)
  override final def catchAll[R, E, A, E2](r: IO[E, A])(f: E => IO[E2, A]): IO[E2, A] = r.onErrorHandleWith(f)
  override final def catchSome[R, E, A, E1 >: E](r: IO[E, A])(f: PartialFunction[E, IO[E1, A]]): IO[E1, A] = r.onErrorRecoverWith(f)
  override final def withFilter[R, E, A](r: IO[E, A])(predicate: A => Boolean)(implicit ev: NoSuchElementException <:< E): IO[E, A] =
    r.flatMap {
      a =>
        if (predicate(a)) IO.pure(a)
        else IO.raiseError(ev(new NoSuchElementException("The value doesn't satisfy the predicate")))
    }

  override final def guarantee[R, E, A](f: IO[E, A], cleanup: IO[Nothing, Unit]): IO[E, A] = f.guarantee(cleanup)
  override final def attempt[R, E, A](r: IO[E, A]): IO[Nothing, Either[E, A]] = r.attempt
  override final def redeemPure[R, E, A, B](r: IO[E, A])(err: E => B, succ: A => B): IO[Nothing, B] = r.redeem(err, succ)

  override final def bracket[R, E, A, B](acquire: IO[E, A])(release: A => IO[Nothing, Unit])(use: A => IO[E, B]): IO[E, B] = acquire.bracket(use)(release)
  override final def bracketCase[R, E, A, B](acquire: IO[E, A])(release: (A, BIOExit[E, B]) => IO[Nothing, Unit])(use: A => IO[E, B]): IO[E, B] = {
    acquire.bracketE(use)((a, exit) => release(a, toIzBIO(exit)))
  }
  override final def guaranteeCase[R, E, A](f: IO[E, A], cleanup: BIOExit[E, A] => IO[Nothing, Unit]): IO[E, A] = f.bracketE(IO.pure)((_, exit) => cleanup(toIzBIO(exit)))

  override final def traverse[R, E, A, B](l: Iterable[A])(f: A => IO[E, B]): IO[E, List[B]] = IO.traverse(l)(f).map(_.toList)
  override final def sequence[R, E, A, B](l: Iterable[IO[E, A]]): IO[E, List[A]] = IO.sequence(l)
  override final def traverse_[R, E, A](l: Iterable[A])(f: A => IO[E, Unit]): IO[E, Unit] = IO.traverse(l)(f).void
  override final def sequence_[R, E](l: Iterable[IO[E, Unit]]): IO[E, Unit] = IO.sequence(l).void

  override final def sandbox[R, E, A](r: IO[E, A]): IO[BIOExit.Failure[E], A] = r.redeemCauseWith(cause => IO.raiseError(fromMonixCause(cause)), a => IO.pure(a))
  override final def yieldNow: IO[Nothing, Unit] = IO.unit
  override final def never: IO[Nothing, Nothing] = IO.never

  override final def race[R, E, A](r1: IO[E, A], r2: IO[E, A]): IO[E, A] = IO.raceMany(List(r1, r2))

  override final def racePair[R, E, A, B](fa: IO[E, A], fb: IO[E, B]): IO[E, Either[(A, BIOFiber[IO, E, B]), (BIOFiber[IO, E, A], B)]] = {
    IO.racePair(fa, fb).flatMap {
      case Left((a, fiberB)) => fiberB.cancel.void.map(_ => Left((a, BIOFiber.fromMonix(fiberB))))
      case Right((fiberA, b)) => fiberA.cancel.void.map(_ => Right((BIOFiber.fromMonix(fiberA), b)))
    }
  }

  override final def async[E, A](register: (Either[E, A] => Unit) => Unit): IO[E, A] = {
    IO.async(cb => register(cb apply Try(_)))
  }
  override final def asyncF[R, E, A](register: (Either[E, A] => Unit) => IO[E, Unit]): IO[E, A] = {
    IO.asyncF(cb => register(cb apply Try(_)))
  }
  override final def asyncCancelable[E, A](register: (Either[E, A] => Unit) => Canceler): IO[E, A] = {
    IO.cancelable[E, A](cb => register(cb apply Try(_)))
  }
  override final def fromFuture[A](mkFuture: ExecutionContext => Future[A]): IO[Throwable, A] = {
    IO.deferFutureAction(mkFuture)
  }
  //this approach I shamelessly ported from javaz
  override final def fromFutureJava[A](javaFuture: => CompletionStage[A]): IO[Throwable, A] = {
    lazy val cs: CompletionStage[A] = javaFuture
    IO.deferTotal {
      val cf = cs.toCompletableFuture
      if (cf.isDone) {
        Try(cf.get()) match {
          case util.Failure(exception) => Task.terminate(exception)
          case util.Success(value) => Task.pure(value)
        }
      } else {
        IO.async {
          cb =>
            cs.handle[Unit] {
              (v: A, t: Throwable) =>
                val io = Option(t).fold[Either[Cause[Throwable], A]](Right(v))(_ => Left(Cause.Error(t)))
                cb(io)
            }; ()
        }
      }
    }
  }
  override final def uninterruptible[R, E, A](r: IO[E, A]): IO[E, A] = r.uncancelable

  override final def parTraverseN[R, E, A, B](maxConcurrent: Int)(l: Iterable[A])(f: A => IO[E, B]): IO[E, List[B]] = IO.parTraverseN(maxConcurrent)(l)(f)
  override final def parTraverseN_[R, E, A, B](maxConcurrent: Int)(l: Iterable[A])(f: A => IO[E, B]): IO[E, Unit] = IO.parTraverseN(maxConcurrent)(l)(f).void
  override final def parTraverse[R, E, A, B](l: Iterable[A])(f: A => IO[E, B]): IO[E, List[B]] = IO.parTraverse(l)(f)
  override final def parTraverse_[R, E, A, B](l: Iterable[A])(f: A => IO[E, B]): IO[E, Unit] = IO.parTraverse(l)(f).void

  override final def zipWithPar[R, E, A, B, C](fa: IO[E, A], fb: IO[E, B])(f: (A, B) => C): IO[E, C] = IO.mapBoth(fa, fb)(f)
  override final def zipPar[R, E, A, B](fa: IO[E, A], fb: IO[E, B]): IO[E, (A, B)] = IO.parZip2(fa, fb)
  override final def zipParLeft[R, E, A, B](fa: IO[E, A], fb: IO[E, B]): IO[E, A] = IO.parZip2(fa, fb).map { case (a, _) => a }
  override final def zipParRight[R, E, A, B](fa: IO[E, A], fb: IO[E, B]): IO[E, B] = IO.parZip2(fa, fb).map { case (_, b) => b }
}
