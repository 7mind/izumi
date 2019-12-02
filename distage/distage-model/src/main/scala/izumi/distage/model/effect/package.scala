package izumi.distage.model

package object effect {
  type DIEffect2[F[_, _]] = DIEffect[F[Throwable, ?]]
  type DIEffectAsync2[F[_, _]] = DIEffectAsync[F[Throwable, ?]]
  type DIEffectRunner2[F[_, _]] = DIEffectRunner[F[Throwable, ?]]
}
