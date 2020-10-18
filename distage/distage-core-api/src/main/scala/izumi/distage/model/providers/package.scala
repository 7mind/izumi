package izumi.distage.model

package object providers {
  @deprecated("Use `distage.Functoid` instead of `distage.ProviderMagnet`", "1.0")
  type ProviderMagnet[+A] = Functoid[A]

  @deprecated("Use `distage.Functoid` instead of `distage.ProviderMagnet`", "1.0")
  final val ProviderMagnet: Functoid.type = Functoid
}
