package izumi.functional.bio

import izumi.functional.bio.data.InterruptAction

import java.util.concurrent.CompletionStage
import scala.concurrent.{ExecutionContext, Future}

trait Async2[F[+_, +_]] extends Concurrent2[F] with WeakAsync2[F] {
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
    * @note Effects created with [[async]], [[asyncF]], [[asyncWithOnInterrupt]],
    *       [[fromFuture]] and [[fromFutureJava]] are INTERRUPTIBLE.
    *
    *       - In case of [[async]] and [[asyncF]] if the current fiber is interrupted,
    *       the registered callback will be simply THROWN AWAY.
    *
    *       - If a cleanup action is required to wind down the async operation safely,
    *       use [[asyncWithOnInterrupt]] to provide a cleanup action.
    *
    *       - If an async operation's callback CANNOT be safely discarded OR interrupted,
    *       wrap your expression in [[Panic2.uninterruptible]].
    *
    * @note to implementors: The effect produced MUST be interruptible.
    */
  override def async[E, A](register: (Either[E, A] => Unit) => Unit): F[E, A]

  /**
    * Same as [[async]], but registration action can use F capabilities.
    * Registration action itself is uninterruptible, but the produced effect is interruptible.
    *
    * @note Effects created with [[async]], [[asyncF]], [[asyncWithOnInterrupt]],
    *       [[fromFuture]] and [[fromFutureJava]] are INTERRUPTIBLE.
    *
    *       - In case of [[async]] and [[asyncF]] if the current fiber is interrupted,
    *       the registered callback will be simply THROWN AWAY.
    *
    *       - If a cleanup action is required to wind down the async operation safely,
    *       use [[asyncWithOnInterrupt]] to provide a cleanup action.
    *
    *       - If an async operation's callback CANNOT be safely discarded OR interrupted,
    *       wrap your expression in [[Panic2.uninterruptible]].
    *
    * @note to implementors: The effect produced MUST be interruptible.
    */
  def asyncF[E, A](register: (Either[E, A] => Unit) => F[E, Unit]): F[E, A]

  /**
    * Just as in [[async]], registers async callback impurely, but also returns an F effect
    * that will be executed to interrupt the async action if the current fiber is interrupted
    *
    * @note Effects created with [[async]], [[asyncF]], [[asyncWithOnInterrupt]],
    *       [[fromFuture]] and [[fromFutureJava]] are INTERRUPTIBLE.
    *
    *       - In case of [[async]] and [[asyncF]] if the current fiber is interrupted,
    *       the registered callback will be simply THROWN AWAY.
    *
    *       - If a cleanup action is required to wind down the async operation safely,
    *       use [[asyncWithOnInterrupt]] to provide a cleanup action.
    *
    *       - If an async operation's callback CANNOT be safely discarded OR interrupted,
    *       wrap your expression in [[Panic2.uninterruptible]].
    *
    * @note to implementors: The effect produced MUST be interruptible.
    */
  def asyncWithOnInterrupt[E, A](register: (Either[E, A] => Unit) => InterruptAction[F]): F[E, A]

  /** @note to implementors: The effect produced MUST be interruptible (cats.effect.IO's fromFuture is not!) */
  override def fromFuture[A](mkFuture: ExecutionContext => Future[A]): F[Throwable, A]

  /** @note to implementors: The effect produced MUST be interruptible and call [[java.util.concurrent.Future.cancel]] on interrupt */
  def fromFutureJava[A](javaFuture: => CompletionStage[A]): F[Throwable, A]

  def currentEC: F[Nothing, ExecutionContext]
  def onEC[E, A](ec: ExecutionContext)(f: F[E, A]): F[E, A]

  // defaults
  override def never: F[Nothing, Nothing] = async(_ => ())

  @deprecated("renamed to asyncWithOnInterrupt", "1.3")
  final def asyncCancelable[E, A](register: (Either[E, A] => Unit) => InterruptAction[F]): F[E, A] = asyncWithOnInterrupt(register)
}
