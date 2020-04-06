package izumi.functional.bio.impl

import java.util.concurrent.CompletionStage

import izumi.functional.bio.{BIOAsync, BIOExit, BIOFiber}
import monix.bio

import scala.concurrent.{ExecutionContext, Future}

object BIOAsyncMonix extends BIOAsyncMonix

// NOTE: what is Local means???
class BIOAsyncMonix extends BIOAsync[bio.BIO] {
  override final def async[E, A](register: (Either[E, A] => Unit) => Unit): bio.BIO[E, A] = {
    bio.BIO.async(cb => register(_.fold(bio.Cause.Error(_), bio.BIO.pure)))
  }

  override def asyncF[R, E, A](register: (Either[E, A] => Unit) => bio.BIO[E, Unit]): bio.BIO[E, A] = ???
  override def asyncCancelable[E, A](register: (Either[E, A] => Unit) => Canceler): bio.BIO[E, A] = ???

  override def fromFuture[A](mkFuture: ExecutionContext => Future[A]): bio.BIO[Throwable, A] = ???
  override def fromFutureJava[A](javaFuture: =>CompletionStage[A]): bio.BIO[Throwable, A] = ???

  override def yieldNow: bio.BIO[Nothing, Unit] = ???

  /** Race two actions, the winner is the first action to TERMINATE, whether by success or failure */
  override def race[R, E, A](r1: bio.BIO[E, A], r2: bio.BIO[E, A]): bio.BIO[E, A] = ???
  override def racePair[R, E, A, B](fa: bio.BIO[E, A], fb: bio.BIO[E, B]): bio.BIO[E, Either[(A, BIOFiber[bio.BIO, E, B]), (BIOFiber[bio.BIO, E, A], B)]] = ???

  override def parTraverseN[R, E, A, B](maxConcurrent: Int)(l: Iterable[A])(f: A => bio.BIO[E, B]): bio.BIO[E, List[B]] = ???
  override def parTraverse[R, E, A, B](l: Iterable[A])(f: A => bio.BIO[E, B]): bio.BIO[E, List[B]] = ???

  override def uninterruptible[R, E, A](r: bio.BIO[E, A]): bio.BIO[E, A] = ???
  //override final def ask[R]: = ???

  override def syncThrowable[A](effect: => A): bio.BIO[Throwable, A] = ???
  override def sync[A](effect: => A): bio.BIO[Nothing, A] = ???
  override def terminate(v: =>Throwable): bio.BIO[Nothing, Nothing] = ???
  override def sandbox[R, E, A](r: bio.BIO[E, A]): bio.BIO[BIOExit.Failure[E], A] = ???

  override def bracketCase[R, E, A, B](acquire: bio.BIO[E, A])(release: (A, BIOExit[E, B]) => bio.BIO[Nothing, Unit])(use: A => bio.BIO[E, B]): bio.BIO[E, B] = ???

  override def fail[E](v: => E): bio.BIO[E, Nothing] = ???

  override def catchAll[R, E, A, E2, A2 >: A](r: bio.BIO[E, A])(f: E => bio.BIO[E2, A2]): bio.BIO[E2, A2] = ???
  override def catchSome[R, E, A, E2 >: E, A2 >: A](r: bio.BIO[E, A])(f: PartialFunction[E, bio.BIO[E2, A2]]): bio.BIO[E2, A2] = ???
  override def flatMap[R, E, A, R2 <: R, E2 >: E, B](r: bio.BIO[E, A])(f: A => bio.BIO[E2, B]): bio.BIO[E2, B] = ???

  override def pure[A](a: A): bio.BIO[Nothing, A] = ???

  override def traverse[R, E, A, B](l: Iterable[A])(f: A => bio.BIO[E, B]): bio.BIO[E, List[B]] = ???
}

//object util {
//  type BIOLocal2[F[Any, _, _]] = BIOLocal[F]
//}
