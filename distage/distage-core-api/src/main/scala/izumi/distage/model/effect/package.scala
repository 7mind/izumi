package izumi.distage.model

package object effect {
  type DIApplicative2[F[_, _]] = DIApplicative[F[Throwable, ?]]
  type DIEffect2[F[_, _]] = DIEffect[F[Throwable, ?]]
  type DIEffectAsync2[F[_, _]] = DIEffectAsync[F[Throwable, ?]]
  type DIEffectRunner2[F[_, _]] = DIEffectRunner[F[Throwable, ?]]

  type DIApplicative3[F[_, _, _]] = DIApplicative[F[Any, Throwable, ?]]
  type DIEffect3[F[_, _, _]] = DIEffect[F[Any, Throwable, ?]]
  type DIEffectAsync3[F[_, _, _]] = DIEffectAsync[F[Any, Throwable, ?]]
  type DIEffectRunner3[F[_, _, _]] = DIEffectRunner[F[Any, Throwable, ?]]
}
