package izumi.functional.bio

import izumi.functional.bio.data.InterruptAction

import java.util.concurrent.CompletionStage
import scala.concurrent.{ExecutionContext, Future}

trait Async2[F[+_, +_]] extends Concurrent2[F] with WeakAsync2[F] {
  override def InnerF: Panic2[F] = this: Panic2[F]

  /**
    * Construct an effect from an asynchronous callback-based API.
    *
    * The callback provided to `register` must be invoked exactly once with either
    * a success value wrapped in Right or an error wrapped in Left.
    *
    * Example:
    * {{{
    *   def readFile[F[+_, +_]: WeakAsync2](path: String): F[Throwable, String] = {
    *     F.async { cb =>
    *       asyncFileReader.read(path)(
    *         onSuccess = content => cb(Right(content)),
    *         onError = err => cb(Left(err))
    *       )
    *     }
    *   }
    * }}}
    */
  def async[E, A](register: (Either[E, A] => Unit) => Unit): F[E, A]
  def asyncF[E, A](register: (Either[E, A] => Unit) => F[E, Unit]): F[E, A]

  /** As in `async`, register async callback impurely, but also return an F effect
    * that will be executed to interrupt the async action if the current fiber is interrupted
    */
  def asyncCancelable[E, A](register: (Either[E, A] => Unit) => InterruptAction[F]): F[E, A]

  def fromFuture[A](mkFuture: ExecutionContext => Future[A]): F[Throwable, A]
  def fromFutureJava[A](javaFuture: => CompletionStage[A]): F[Throwable, A]

  def currentEC: F[Nothing, ExecutionContext]
  def onEC[E, A](ec: ExecutionContext)(f: F[E, A]): F[E, A]

  // defaults
  override def never: F[Nothing, Nothing] = async(_ => ())
}
