package izumi.distage.model

package object definition {
  /** Direct re-export of [[izumi.functional.lifecycle.Lifecycle]] — bifunctor-shaped
    * (`F[+_, +_]`, F invariant, `+E` typed error channel, `+A` value).
    */
  type Lifecycle[F[+_, +_], +E, +A] = izumi.functional.lifecycle.Lifecycle[F, E, A]
  final val Lifecycle: izumi.functional.lifecycle.Lifecycle.type = izumi.functional.lifecycle.Lifecycle

  type Lifecycle2[F[+_, +_], +E, +A] = izumi.functional.lifecycle.Lifecycle2[F, E, A]
  type Lifecycle3[F[-_, +_, +_], R, +E, +A] = izumi.functional.lifecycle.Lifecycle3[F, R, E, A]
}
