package izumi.distage.model

package object definition {
  type Lifecycle[+F[_], +OuterResource] = izumi.functional.lifecycle.Lifecycle[F, OuterResource]
  final val Lifecycle = izumi.functional.lifecycle.Lifecycle

  type Lifecycle2[+F[+_, +_], +E, +A] = izumi.functional.lifecycle.Lifecycle2[F, E, A]
  type Lifecycle3[+F[-_, +_, +_], -R, +E, +A] = izumi.functional.lifecycle.Lifecycle3[F, R, E, A]

}
