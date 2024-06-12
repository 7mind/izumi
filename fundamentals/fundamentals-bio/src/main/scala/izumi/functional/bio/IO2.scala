package izumi.functional.bio

import scala.collection.compat.*
import scala.util.Try

trait IO2[F[+_, +_]] extends Panic2[F] {

  /**
    * Capture a side-effectful block of code that can throw exceptions
    *
    * @note `sync` means `synchronous`, that is, a blocking CPU effect, as opposed to a non-blocking
    *       [[izumi.functional.bio.Async3#async asynchronous]] effect or a
    *       long blocking I/O effect ([[izumi.functional.bio.BlockingIO2#syncBlocking]])
    */
  def syncThrowable[A](effect: => A): F[Throwable, A]

  /**
    * Capture an _exception-safe_ side-effect such as memory mutation or randomness
    *
    * @example
    * {{{
    * import izumi.functional.bio.F
    *
    * val exceptionSafeArrayAllocation: F[Nothing, Array[Byte]] = {
    *   F.sync(new Array(256))
    * }
    * }}}
    * @note If you're not completely sure that a captured block can't throw, use [[syncThrowable]]
    * @note `sync` means `synchronous`, that is, a blocking CPU effect, as opposed to a non-blocking
    *       [[izumi.functional.bio.Async3#async asynchronous]] effect or a
    *       long blocking I/O effect ([[izumi.functional.bio.BlockingIO2#syncBlocking]])
    */
  def sync[A](effect: => A): F[Nothing, A]

  /** Capture a side-effectful block of code that can throw exceptions and returns another effect */
  def suspend[A](effect: => F[Throwable, A]): F[Throwable, A] = flatten(syncThrowable(effect))

  /** Capture an _exception-safe_ side-effect that returns another effect */
  def suspendSafe[E, A](effect: => F[E, A]): F[E, A] = flatten(sync(effect))

  @inline final def apply[A](effect: => A): F[Throwable, A] = syncThrowable(effect)

  // defaults
  override def fromEither[E, A](effect: => Either[E, A]): F[E, A] = flatMap(sync(effect)) {
    case Left(e) => fail(e): F[E, A]
    case Right(v) => pure(v): F[E, A]
  }
  override def fromOption[E, A](errorOnNone: => E)(effect: => Option[A]): F[E, A] = {
    flatMap(sync(effect))(opt => opt.fold(fail(errorOnNone): F[E, A])(pure))
  }
  override def fromTry[A](effect: => Try[A]): F[Throwable, A] = {
    syncThrowable(effect.get)
  }

  override protected def accumulateErrorsImpl[ColL[_], ColR[x] <: IterableOnce[x], E, E1, A, B, B1, AC](
    col: ColR[A]
  )(effect: A => F[E, B],
    onLeft: E => IterableOnce[E1],
    init: AC,
    onRight: (AC, B) => AC,
    end: AC => B1,
  )(implicit buildL: Factory[E1, ColL[E1]]
  ): F[ColL[E1], B1] = {
    suspendSafe {
      val bad = buildL.newBuilder

      val iterator = col.iterator
      var good = init
      var allGood = true

      def go(): F[ColL[E1], B1] = {
        suspendSafe(if (iterator.hasNext) {
          redeem(effect(iterator.next()))(
            e =>
              suspendSafe {
                allGood = false
                bad ++= onLeft(e)
                go()
              },
            v =>
              suspendSafe {
                good = onRight(good, v)
                go()
              },
          )
        } else {
          if (allGood) {
            pure(end(good))
          } else {
            fail(bad.result())
          }
        })
      }

      go()
    }
  }
}
