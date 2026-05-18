package izumi.functional

package object lifecycle {
  /** Alias retained for compatibility with downstream that expects the old bifunctor-shape alias.
    * Now that [[Lifecycle]] itself is bifunctor-shaped (`Lifecycle[F[+_, +_], +E, +A]`, F invariant),
    * this is a direct type alias.
    */
  type Lifecycle2[F[+_, +_], +E, +A] = Lifecycle[F, E, A]

  /** Alias retained for compatibility — projects out the ZIO-style `R` parameter so the result is
    * a bifunctor lifecycle over `F[R, +_, +_]`.
    */
  type Lifecycle3[F[-_, +_, +_], R, +E, +A] = Lifecycle[λ[(`+e`, `+a`) => F[R, e, a]], E, A]
}
