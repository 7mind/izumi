package izumi.functional.bio

import scala.concurrent.{ExecutionContext, Future}

/**
  * Parallel operations combined with basic async capabilities.
  *
  * This typeclass provides parallel execution ([[Parallel2]]) along with
  * the ability to integrate asynchronous callback-based APIs and Scala Futures,
  * but without requiring the full error handling hierarchy of [[IO2]] and [[Panic2]].
  *
  * @see [[Async2]] for full async capabilities including cancelation and execution context control
  */
trait WeakAsync2[F[+_, +_]] extends IO2[F] with Parallel2[F] {
  override def InnerF: Panic2[F] = this

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

  def fromFuture[A](mkFuture: ExecutionContext => Future[A]): F[Throwable, A]

  // defaults
  def never: F[Nothing, Nothing] = async(_ => ())

  @inline final def fromFuture[A](mkFuture: => Future[A]): F[Throwable, A] = fromFuture(_ => mkFuture)
}
