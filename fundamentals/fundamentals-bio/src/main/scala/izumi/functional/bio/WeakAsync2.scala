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
    *
    * @note Effects created with [[async]], [[Async2.asyncF]], [[Async2.asyncWithOnInterrupt]],
    *       [[fromFuture]] and [[Async2.fromFutureJava]] are INTERRUPTIBLE.
    *
    *       - In case of [[async]] and [[Async2.asyncF]] if the current fiber is interrupted,
    *       the registered callback will be simply THROWN AWAY.
    *
    *       - If a cleanup action is required to wind down the async operation safely,
    *       use [[Async2.asyncWithOnInterrupt]] to provide a cleanup action.
    *
    *       - If an async operation's callback CANNOT be safely discarded OR interrupted,
    *       wrap your expression in [[Panic2.uninterruptible]].
    *
    * @note to implementors: The effect produced MUST be interruptible.
    */
  def async[E, A](register: (Either[E, A] => Unit) => Unit): F[E, A]

  /** @note to implementors: The effect produced MUST be interruptible (cats.effect.IO's fromFuture is not!). */
  def fromFuture[A](mkFuture: ExecutionContext => Future[A]): F[Throwable, A]

  // defaults
  def never: F[Nothing, Nothing] = async(_ => ())

  @inline final def fromFuture[A](mkFuture: => Future[A]): F[Throwable, A] = fromFuture(_ => mkFuture)
}
