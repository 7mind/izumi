package izumi.distage.model

package object definition {
  @deprecated("Use distage.Lifecycle.Basic", "0.11")
  type DIResource[+F[_], A] = Lifecycle.Basic[F, A]

  @deprecated("Use distage.Lifecycle", "0.11")
  lazy val DIResource: Lifecycle.type = Lifecycle
}
