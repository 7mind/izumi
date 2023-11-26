package izumi.functional.bio

import java.util.concurrent.CompletionStage

import scala.concurrent.{ExecutionContext, Future}

trait Async2[F[+_, +_]] extends Concurrent2[F] with IO2[F] {
  override def InnerF: Panic2[F] = this

  final type Canceler = F[Nothing, Unit]

  def async[E, A](register: (Either[E, A] => Unit) => Unit): F[E, A]
  def asyncF[E, A](register: (Either[E, A] => Unit) => F[E, Unit]): F[E, A]
  def asyncCancelable[E, A](register: (Either[E, A] => Unit) => Canceler): F[E, A]

  def fromFuture[A](mkFuture: ExecutionContext => Future[A]): F[Throwable, A]
  def fromFutureJava[A](javaFuture: => CompletionStage[A]): F[Throwable, A]

  def currentEC: F[Nothing, ExecutionContext]
  def onEC[E, A](ec: ExecutionContext)(f: F[E, A]): F[E, A]

  // defaults
  override def never: F[Nothing, Nothing] = async(_ => ())

  @inline final def fromFuture[A](mkFuture: => Future[A]): F[Throwable, A] = fromFuture(_ => mkFuture)
}
