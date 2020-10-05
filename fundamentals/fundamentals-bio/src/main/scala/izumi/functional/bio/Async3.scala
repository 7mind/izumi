package izumi.functional.bio

import java.util.concurrent.CompletionStage

import scala.concurrent.{ExecutionContext, Future}

trait Async3[F[-_, +_, +_]] extends Concurrent3[F] with IO3[F] {
  override def InnerF: Panic3[F] = this

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
