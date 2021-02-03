package izumi.distage.model

package object definition {
  type Lifecycle2[+F[_, _], E, +A] = Lifecycle[F[E, ?], A]
  type Lifecycle3[+F[_, _, _], R, E, +A] = Lifecycle[F[R, E, ?], A]

  @deprecated("Use distage.Lifecycle.Basic", "1.0")
  type DIResource[+F[_], A] = Lifecycle.Basic[F, A]

  @deprecated("Use distage.Lifecycle", "1.0")
  lazy val DIResource: Lifecycle.type = Lifecycle
}
