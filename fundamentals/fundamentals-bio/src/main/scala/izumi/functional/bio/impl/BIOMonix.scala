package izumi.functional.bio.impl

import izumi.functional.bio.BIOExit.{Success, Termination, Trace}
import izumi.functional.bio.{BIOBracket, BIOExit}
import monix.bio
import monix.bio.{BIO, Cause}

import scala.util.Try

object BIOMonix extends BIOMonix

class BIOMonix extends BIOBracket[bio.BIO] {
  //  override final def async[E, A](register: (Either[E, A] => Unit) => Unit): bio.BIO[E, A] = {
  //    bio.BIO.async(cb => register(cb apply _.leftMap(bio.Cause.Error(_))))
  //  }
  //
  //  override def asyncF[R, E, A](register: (Either[E, A] => Unit) => bio.BIO[E, Unit]): bio.BIO[E, A] = {
  //    bio.BIO.asyncF(cb => register(cb apply _.leftMap(bio.Cause.Error(_))))
  //  }
  //
  //  override def asyncCancelable[E, A](register: (Either[E, A] => Unit) => Canceler): bio.BIO[E, A] = {
  //    bio.BIO.cancelable[E, A](cb => register(cb apply _.leftMap(bio.Cause.Error(_))))
  //  }
  //
  //  override def fromFuture[A](mkFuture: ExecutionContext => Future[A]): bio.BIO[Throwable, A] = {
  //    bio.BIO.deferFutureAction(mkFuture)
  //  }
  //
  //  //this approach I shamelessly ported from javaz
  //  override def fromFutureJava[A](javaFuture: => CompletionStage[A]): bio.BIO[Throwable, A] = {
  //    lazy val cs: CompletionStage[A] = javaFuture
  //    bio.BIO.deferTotal {
  //      val cf = cs.toCompletableFuture
  //      if (cf.isDone) {
  //        Try(cf.get()) match {
  //          case Failure(exception) => bio.Task.terminate(exception)
  //          case Success(value) => bio.Task.pure(value)
  //        }
  //      } else {
  //        bio.Task.async {
  //          cb =>
  //            cs.handle[Unit] { (v: A, t: Throwable) =>
  //              val io = Option(t).fold[Either[bio.Cause[Throwable], A]](Right(v))(_ => Left(bio.Cause.Error(t)))
  //              cb(io)
  //            }
  //        }
  //      }
  //    }
  //  }
  //
  //  override def yieldNow: bio.BIO[Nothing, Unit] = bio.BIO.unit
  //
  //  /** Race two actions, the winner is the first action to TERMINATE, whether by success or failure */
  //  override def race[R, E, A](r1: bio.BIO[E, A], r2: bio.BIO[E, A]): bio.BIO[E, A] = bio.BIO.raceMany(List(r1, r2))
  //
  //  // FIXME: fibers....
  //  override def racePair[R, E, A, B](fa: bio.BIO[E, A], fb: bio.BIO[E, B]): bio.BIO[E, Either[(A, BIOFiber[bio.BIO, E, B]), (BIOFiber[bio.BIO, E, A], B)]] = {
  //    //    bio.BIO.racePair(fa, fb).map {
  //    //      case Left((a, fiber)) => Left(a, BIOFiber.fromMonix)
  //    //      case Right(fiber, b) =>
  //    //    }
  //    ???
  //  }
  //
  //  // TODO: Monix doesn't have parTraverse analogue(?!)...
  //  override def parTraverseN[R, E, A, B](maxConcurrent: Int)(l: Iterable[A])(f: A => bio.BIO[E, B]): bio.BIO[E, List[B]] = ???
  //  override def parTraverse[R, E, A, B](l: Iterable[A])(f: A => bio.BIO[E, B]): bio.BIO[E, List[B]] = ???
  //
  //  override def uninterruptible[R, E, A](r: bio.BIO[E, A]): bio.BIO[E, A] = r.uncancelable
  //
  //  override def syncThrowable[A](effect: => A): bio.BIO[Throwable, A] = bio.BIO.suspend(bio.Task(effect))
  //
  //  override def sync[A](effect: => A): bio.BIO[Nothing, A] = bio.BIO.suspendTotal(bio.BIO.now(effect))
  //
  //  override def terminate(v: => Throwable): bio.BIO[Nothing, Nothing] = bio.BIO.terminate(v)
  //
  //  override def sandbox[R, E, A](r: bio.BIO[E, A]): bio.BIO[BIOExit.Failure[E], A] = ???

  override def bracketCase[R, E, A, B](acquire: bio.BIO[E, A])(release: (A, BIOExit[E, B]) => bio.BIO[Nothing, Unit])(use: A => bio.BIO[E, B]): bio.BIO[E, B] = {
    acquire.bracketE(use)((a, exit) => release(a, toIzBIO(exit)))
  }

  override def fail[E](v: => E): bio.BIO[E, Nothing] = bio.BIO.raiseError(v)

  override def pure[A](a: A): bio.BIO[Nothing, A] = bio.BIO.pure(a)

  override def traverse[R, E, A, B](l: Iterable[A])(f: A => bio.BIO[E, B]): bio.BIO[E, List[B]] = bio.BIO.traverse(l)(f).map(_.toList)

  override def catchAll[R, E, A, E2](r: bio.BIO[E, A])(f: E => bio.BIO[E2, A]): bio.BIO[E2, A] = r.onErrorHandleWith(f)

  override def catchSome[R, E, A, E1 >: E](r: bio.BIO[E, A])(f: PartialFunction[E, bio.BIO[E1, A]]): bio.BIO[E1, A] = r.onErrorRecoverWith(f)

  override def fromEither[E, V](effect: => Either[E, V]): bio.BIO[E, V] = BIO.fromEither(effect)

  override def fromOption[E, A](errorOnNone: => E)(effect: => Option[A]): bio.BIO[E, A] = bio.BIO.fromEither(effect.toRight(errorOnNone))

  override def fromTry[A](effect: => Try[A]): bio.BIO[Throwable, A] = bio.BIO.fromTry(effect)

  override def flatMap[R, E, A, B](r: bio.BIO[E, A])(f: A => bio.BIO[E, B]): bio.BIO[E, B] = r.flatMap(f)

  private[this] def toIzBIO[E, A](exit: Either[Option[Cause[E]], A]): BIOExit[E, A] = {
    exit match {
      case Left(None) => Termination(new Throwable("The task was cancelled."), Trace.empty)
      case Left(Some(error)) => error match {
        case Cause.Error(value) => BIOExit.Error(value, Trace.empty)
        case Cause.Termination(value) => BIOExit.Termination(value, Trace.empty)
      }
      case Right(value) => Success(value)
    }
  }
}

