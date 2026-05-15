package izumi.functional.bio

import izumi.fundamentals.platform.functional.Identity

/** Typeclass governing the `F[A] <-> Bifunctorized[F, Throwable, A]` round-trip.
  *
  * Provides the (de-)submerging logic in a single place: the identity instance
  * (no submerging — Goal 4 zero-cost path for real bifunctors and `F`s without
  * an `ApplicativeError`) and the cats-mediated instance (submerges via
  * `ApplicativeError.adaptError`, gated on `cats.ApplicativeError[F, Throwable]`
  * and `TagK[F]`).
  *
  * Both the explicit methods [[Bifunctorized.bifunctorize]] / [[Bifunctorized.debifunctorize]]
  * and the implicit conversions [[Bifunctorized.bifunctorizeConversion]] /
  * [[Bifunctorized.debifunctorizeConversion]] take a `Bifunctorize[F]` implicitly
  * and delegate, so there is a single source of truth.
  *
  * Goal 5 ("No-More-Orphans") is preserved: this file does NOT import cats.
  * The cats-mediated instance lives in [[CatsToBIOConversions]] and uses the
  * "No-More-Orphans" trick (`izumi.fundamentals.orphans.\`cats.ApplicativeError\``)
  * so users without cats on their classpath are not forced to depend on it.
  */
trait Bifunctorize[F[_]] {
  def bifunctorize[A](fa: F[A]): Bifunctorized[F, Throwable, A]
  def debifunctorize[A](b: Bifunctorized[F, Throwable, A]): F[A]
}

object Bifunctorize extends LowPriorityBifunctorizeInstances {
  @inline def apply[F[_]](implicit ev: Bifunctorize[F]): Bifunctorize[F] = ev
}

trait LowPriorityBifunctorizeInstances {

  /** Identity instance — used for any `F[_]` without a higher-priority instance in scope
    * (notably: real bifunctors used through their bifunctor surface, and any `F` whose
    * `ApplicativeError` is not imported).
    *
    * Both directions are reinterpret-casts. Goal 4 (`bifunctorize(realBifunctor) eq realBifunctor`)
    * holds via this path.
    *
    * The same singleton is cast to every `Bifunctorize[F]` slot via `asInstanceOf` —
    * sound because `Bifunctorize`'s methods only project between abstract types `F[A]`
    * and `Bifunctorized[F, Throwable, A]` that erase identically at the JVM level.
    */
  @inline implicit final def identityBifunctorize[F[_]]: Bifunctorize[F] =
    identityBifunctorizeInstance.asInstanceOf[Bifunctorize[F]]

  // Single shared instance, cast to every `Bifunctorize[F]` slot. We pick
  // `Identity[A] = A` (`fundamentals.platform.functional.Identity`) as the concrete `F`:
  // it erases to `A`, so the JVM-level method signature is
  //   bifunctorize(Object): Object
  // which accepts any reference type. Without this, a concrete `F` like `List` would
  // erase to `bifunctorize(List): Object` and the bridge method would `CHECKCAST` to
  // `List`, throwing `ClassCastException` when the caller passed a `ZIO` or `Try`.
  // The methods only forward through reinterpret-casts, so the underlying concrete `F`
  // of the singleton is never observed at runtime.
  private val identityBifunctorizeInstance: Bifunctorize[Identity] = new Bifunctorize[Identity] {
    override def bifunctorize[A](fa: Identity[A]): Bifunctorized[Identity, Throwable, A] =
      Bifunctorized.assert(fa)
    override def debifunctorize[A](b: Bifunctorized[Identity, Throwable, A]): Identity[A] =
      b.asInstanceOf[Identity[A]]
  }
}
