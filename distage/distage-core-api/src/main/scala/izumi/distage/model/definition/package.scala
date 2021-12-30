package izumi.distage.model

package object definition {
  type Lifecycle2[+F[+_, +_], +E, +A] = Lifecycle[F[E, _], A]
  type Lifecycle3[+F[-_, +_, +_], -R, +E, +A] = Lifecycle[F[R, E, _], A]
}
