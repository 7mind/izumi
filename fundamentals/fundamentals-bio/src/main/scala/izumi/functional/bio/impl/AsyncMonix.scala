package izumi.functional.bio.impl

import java.util.concurrent.CompletionStage

import izumi.functional.bio.Exit.MonixExit._
import izumi.functional.bio.{Async2, Exit, Fiber2}
import monix.bio.{Cause, IO, Task}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

object AsyncMonix extends AsyncMonix

class AsyncMonix extends Async2[IO] {
  @inline override final def InnerF: this.type = this

  override final def unit: IO[Nothing, Unit] = IO.unit
  override final def pure[A](a: A): IO[Nothing, A] = IO.pure(a)
  override final def sync[A](effect: => A): IO[Nothing, A] = IO.evalTotal(effect)
  override final def syncThrowable[A](effect: => A): IO[Throwable, A] = IO.eval(effect)
  override final def suspend[R, A](effect: => IO[Throwable, A]): IO[Throwable, A] = IO.suspend(effect)

  override final def fail[E](v: => E): IO[E, Nothing] = IO.raiseError(v)
  override final def terminate(v: => Throwable): IO[Nothing, Nothing] = IO.terminate(v)

  override final def fromEither[E, V](effect: => Either[E, V]): IO[E, V] = IO.suspendTotal(IO.fromEither(effect))
  override final def fromOption[E, A](errorOnNone: => E)(effect: => Option[A]): IO[E, A] = IO.suspendTotal(IO.fromEither(effect.toRight(errorOnNone)))
  override final def fromTry[A](effect: => Try[A]): IO[Throwable, A] = IO.suspendTotal(IO.fromTry(effect))

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
  override final def map2[R, E, A, B, C](r1: IO[E, A], r2: => IO[E, B])(f: (A, B) => C): IO[E, C] = IO.map2(r1, r2)(f)

  override final def leftMap2[R, E, A, E2, E3](firstOp: IO[E, A], secondOp: => IO[E2, A])(f: (E, E2) => E3): IO[E3, A] =
    firstOp.onErrorHandleWith(e => secondOp.mapError(f(e, _)))
  override final def orElse[R, E, A, E2](r: IO[E, A], f: => IO[E2, A]): IO[E2, A] = r.onErrorHandleWith(_ => f)

  override final def redeem[R, E, A, E2, B](r: IO[E, A])(err: E => IO[E2, B], succ: A => IO[E2, B]): IO[E2, B] = r.redeemWith(err, succ)
  override final def catchAll[R, E, A, E2](r: IO[E, A])(f: E => IO[E2, A]): IO[E2, A] = r.onErrorHandleWith(f)
  override final def catchSome[R, E, A, E1 >: E](r: IO[E, A])(f: PartialFunction[E, IO[E1, A]]): IO[E1, A] = r.onErrorRecoverWith(f)

  override final def guarantee[R, E, A](f: IO[E, A], cleanup: IO[Nothing, Unit]): IO[E, A] = f.guarantee(cleanup)
  override final def attempt[R, E, A](r: IO[E, A]): IO[Nothing, Either[E, A]] = r.attempt
  override final def redeemPure[R, E, A, B](r: IO[E, A])(err: E => B, succ: A => B): IO[Nothing, B] = r.redeem(err, succ)

  override final def bracket[R, E, A, B](acquire: IO[E, A])(release: A => IO[Nothing, Unit])(use: A => IO[E, B]): IO[E, B] = {
    acquire.bracket(use = use)(release = release)
  }
  override final def bracketCase[R, E, A, B](acquire: IO[E, A])(release: (A, Exit[E, B]) => IO[Nothing, Unit])(use: A => IO[E, B]): IO[E, B] = {
    acquire.bracketE(use = use)(release = (a, exit) => release(a, toExit(exit)))
  }
  override final def guaranteeCase[R, E, A](f: IO[E, A], cleanup: Exit[E, A] => IO[Nothing, Unit]): IO[E, A] = {
    IO.unit.bracketE(use = _ => f)(release = (_, exit) => cleanup(toExit(exit)))
  }

  override final def traverse[R, E, A, B](l: Iterable[A])(f: A => IO[E, B]): IO[E, List[B]] = IO.traverse(l)(f)
  override final def sequence[R, E, A, B](l: Iterable[IO[E, A]]): IO[E, List[A]] = IO.sequence(l)
  override final def traverse_[R, E, A](l: Iterable[A])(f: A => IO[E, Unit]): IO[E, Unit] = IO.traverse(l)(f).void
  override final def sequence_[R, E](l: Iterable[IO[E, Unit]]): IO[E, Unit] = IO.sequence(l).void

  override final def sandbox[R, E, A](r: IO[E, A]): IO[Exit.Failure[E], A] = {
    r.redeemCauseWith(cause => IO.raiseError(toExit(cause)), a => IO.pure(a))
  }
  override final def yieldNow: IO[Nothing, Unit] = IO.shift
  override final def never: IO[Nothing, Nothing] = IO.never

  override final def race[R, E, A](r1: IO[E, A], r2: IO[E, A]): IO[E, A] = IO.race(r1, r2).map(_.merge)

  override final def racePair[R, E, A, B](fa: IO[E, A], fb: IO[E, B]): IO[E, Either[(A, Fiber2[IO, E, B]), (Fiber2[IO, E, A], B)]] = {
    IO.racePair(fa, fb).map {
      case Left((a, fiberB)) => Left((a, Fiber2.fromMonix(fiberB)))
      case Right((fiberA, b)) => Right((Fiber2.fromMonix(fiberA), b))
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
  override final def zipParLeft[R, E, A, B](fa: IO[E, A], fb: IO[E, B]): IO[E, A] = IO.parMap2(fa, fb)((a, _) => a)
  override final def zipParRight[R, E, A, B](fa: IO[E, A], fb: IO[E, B]): IO[E, B] = IO.parMap2(fa, fb)((_, b) => b)
}
