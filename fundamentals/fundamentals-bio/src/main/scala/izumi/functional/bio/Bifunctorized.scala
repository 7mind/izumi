package izumi.functional.bio

import scala.language.implicitConversions
import scala.reflect.ClassTag

object Bifunctorized {

  /** Opaque newtype lifting a monofunctor effect type `F[_]` into a bifunctor.
    *
    * Erased to `Any`; runtime identity is preserved (`bifunctorize(fa) eq fa`),
    * so this is zero-cost over the underlying `F[A]`.
    *
    * Construction goes through [[bifunctorize]] / [[assert]]. The reverse direction
    * is [[debifunctorize]] / [[BifunctorizedSyntax.toMonofunctor]] / [[BifunctorizedOps.unwrap]].
    *
    * No cats imports here — Goal 5 ("No-More-Orphans") relies on this file being usable
    * on a no-cats classpath.
    */
  type Bifunctorized[F[_], +E, +A]

  /** Unchecked reinterpret cast. Internal escape hatch used by `bifunctorize`
    * and conversion-typeclass implementations that have already encoded their
    * own error channel.
    */
  private[bio] def assert[F[_], E, A](fa: F[A]): Bifunctorized[F, E, A] =
    fa.asInstanceOf[Bifunctorized[F, E, A]]

  /** Lift a monofunctor `F[A]` into a bifunctor with the Throwable error channel exposed.
    *
    * PR-01 implementation: identity reinterpret-cast (no submerging). Submerging is added
    * in PR-04 via instance-method paths, not here. Holds Goal 4 (`bifunctorize(fa) eq fa`).
    */
  def bifunctorize[F[_], A](fa: F[A]): Bifunctorized[F, Throwable, A] =
    assert(fa)

  /** Project a `Bifunctorized[F, Throwable, A]` back to the underlying `F[A]`. */
  def debifunctorize[F[_], A](b: Bifunctorized[F, Throwable, A]): F[A] =
    b.asInstanceOf[F[A]]

  /** Implicit `ClassTag` shim. Reflects the runtime class of the underlying `F[A]`.
    *
    * `Bifunctorized[F, E, A]` is an abstract type, so the compiler's `ClassTag` materializer
    * cannot synthesize one directly. We delegate to `ClassTag[F[A]]` — macro-derivable for any
    * concrete `F` and `A` — and cast. This is honest: a `Bifunctorized[F, E, A]` value at
    * runtime IS an `F[A]`. In particular for `F = Identity`, `A = Int` the underlying value
    * is a primitive `Int`, and the derived `ClassTag` correctly carries `classOf[Int]`.
    */
  implicit def getClassTag[F[_], E, A](implicit underlying: ClassTag[F[A]]): ClassTag[Bifunctorized[F, E, A]] =
    underlying.asInstanceOf[ClassTag[Bifunctorized[F, E, A]]]

  /** Implicit conversion auto-lifts `F[A]` to `Bifunctorized[F, Throwable, A]` at expected-type sites. */
  implicit def bifunctorizeConversion[F[_], A](fa: F[A]): Bifunctorized[F, Throwable, A] =
    bifunctorize(fa)

  /** Implicit conversion auto-projects `Bifunctorized[F, Throwable, A]` to `F[A]` at expected-type sites. */
  implicit def debifunctorizeConversion[F[_], A](b: Bifunctorized[F, Throwable, A]): F[A] =
    debifunctorize(b)

  /** `.toMonofunctor` syntax on `Bifunctorized[F, Throwable, A]`, available wherever the companion is imported. */
  implicit final class BifunctorizedSyntax[F[_], A](private val b: Bifunctorized[F, Throwable, A]) extends AnyVal {
    @inline def toMonofunctor: F[A] = debifunctorize(b)
  }

  /** `.unwrap` syntax on any `Bifunctorized[F, E, A]` (matches prior-art `CatsConversionsOps.unwrap`).
    * Internal-flavour: returns the raw `F[A]` regardless of the `E` parameter.
    */
  implicit final class BifunctorizedOps[F[_], E, A](private val b: Bifunctorized[F, E, A]) extends AnyVal {
    @inline def unwrap: F[A] = b.asInstanceOf[F[A]]
  }

}
