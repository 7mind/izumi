package izumi.functional.bio

import java.util.concurrent.CompletionStage

import scala.concurrent.{ExecutionContext, Future}

trait BIOConcurrent3[F[-_, +_, +_]] extends BIOParallel3[F] {
  override def InnerF: BIOPanic3[F]

  /** Race two actions, the winner is the first action to TERMINATE, whether by success or failure */
  def race[R, E, A](r1: F[R, E, A], r2: F[R, E, A]): F[R, E, A]

  /** Race two actions, the winner is the first action to TERMINATE, whether by success or failure */
  def racePair[R, E, A, B](fa: F[R, E, A], fb: F[R, E, B]): F[R, E, Either[(A, BIOFiber3[F, E, B]), (BIOFiber3[F, E, A], B)]]

  def uninterruptible[R, E, A](r: F[R, E, A]): F[R, E, A]

  def yieldNow: F[Any, Nothing, Unit]

  def never: F[Any, Nothing, Nothing]
}

trait BIOAsync3[F[-_, +_, +_]] extends BIOConcurrent3[F] with BIO3[F] {
  override def InnerF: BIOPanic3[F] = this

  final type Canceler = F[Any, Nothing, Unit]

  def async[E, A](register: (Either[E, A] => Unit) => Unit): F[Any, E, A]
  def asyncF[R, E, A](register: (Either[E, A] => Unit) => F[R, E, Unit]): F[R, E, A]
  def asyncCancelable[E, A](register: (Either[E, A] => Unit) => Canceler): F[Any, E, A]

  def fromFuture[A](mkFuture: ExecutionContext => Future[A]): F[Any, Throwable, A]
  def fromFutureJava[A](javaFuture: => CompletionStage[A]): F[Any, Throwable, A]

  // defaults
  override def never: F[Any, Nothing, Nothing] = async(_ => ())

  @inline final def fromFuture[A](mkFuture: => Future[A]): F[Any, Throwable, A] = fromFuture(_ => mkFuture)
}
