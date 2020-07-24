package izumi.distage.model

package object providers {
  @deprecated("Use izumi.distage.model.providers.Functoid instead of ProviderMagnet", "0.11.0")
  type ProviderMagnet[+A] = Functoid[A]

  @deprecated("Use izumi.distage.model.providers.Functoid instead of ProviderMagnet", "0.11.0")
  final val ProviderMagnet = Functoid
}
