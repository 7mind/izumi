package izumi.functional.bio

import scala.util.Try

trait IO3[F[-_, +_, +_]] extends Panic3[F] {
  final type Or[+E, +A] = F[Any, E, A]
  final type Just[+A] = F[Any, Nothing, A]

  /**
    * Capture a side-effectful block of code that can throw exceptions
    *
    * @note `sync` means `synchronous`, that is, a blocking CPU effect, as opposed to a non-blocking
    *        [[izumi.functional.bio.Async3#async asynchronous]] effect or a
    *        long blocking I/O effect ([[izumi.functional.bio.BlockingIO3#syncBlocking]])
    */
  def syncThrowable[A](effect: => A): F[Any, Throwable, A]

  /**
    * Capture an _exception-safe_ side-effect such as memory mutation or randomness
    *
    * @example
    * {{{
    * import izumi.functional.bio.F
    *
    * val referentiallyTransparentArrayAllocation: F[Nothing, Array[Byte]] = {
    *   F.sync(new Array(256))
    * }
    * }}}
    *
    * @note If you're not completely sure that a captured block can't throw, use [[syncThrowable]]
    * @note `sync` means `synchronous`, that is, a blocking CPU effect, as opposed to a non-blocking
    *       [[izumi.functional.bio.Async3#async asynchronous]] effect or a
    *       long blocking I/O effect ([[izumi.functional.bio.BlockingIO3#syncBlocking]])
    */
  def sync[A](effect: => A): F[Any, Nothing, A]

  def suspend[R, A](effect: => F[R, Throwable, A]): F[R, Throwable, A] = flatten(syncThrowable(effect))

  @inline final def apply[A](effect: => A): F[Any, Throwable, A] = syncThrowable(effect)

  // defaults
  override def fromEither[E, A](effect: => Either[E, A]): F[Any, E, A] = flatMap(sync(effect)) {
    case Left(e) => fail(e): F[Any, E, A]
    case Right(v) => pure(v): F[Any, E, A]
  }
  override def fromOption[E, A](errorOnNone: => E)(effect: => Option[A]): F[Any, E, A] = {
    flatMap(sync(effect))(e => fromEither(e.toRight(errorOnNone)))
  }
  override def fromTry[A](effect: => Try[A]): F[Any, Throwable, A] = {
    syncThrowable(effect.get)
  }
}
