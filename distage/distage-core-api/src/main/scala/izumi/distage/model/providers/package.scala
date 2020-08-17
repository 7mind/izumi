package izumi.distage.model

package object providers {
  @deprecated("Use `distage.Functoid` instead of ProviderMagnet", "0.11.0")
  type ProviderMagnet[+A] = Functoid[A]

  @deprecated("Use `distage.Functoid` instead of ProviderMagnet", "0.11.0")
  final val ProviderMagnet: Functoid.type = Functoid
}
