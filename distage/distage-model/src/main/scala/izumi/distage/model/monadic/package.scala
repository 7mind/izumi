package izumi.distage.model

package object monadic {
  type DIEffect2[F[_, _]] = DIEffect[F[Throwable, ?]]
  type DIEffectAsync2[F[_, _]] = DIEffectAsync[F[Throwable, ?]]
  type DIEffectRunner2[F[_, _]] = DIEffectRunner[F[Throwable, ?]]
}
