package izumi.functional.bio.instances

import java.util.concurrent.CompletionStage

import izumi.functional.bio.BIOFiber3

import scala.concurrent.{ExecutionContext, Future}

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
