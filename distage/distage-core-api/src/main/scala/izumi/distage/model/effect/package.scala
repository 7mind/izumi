package izumi.distage.model

package object effect {
  type QuasiEffect2[F[_, _]] = QuasiEffect[F[Throwable, ?]]
  type QuasiEffect3[F[_, _, _]] = QuasiEffect[F[Any, Throwable, ?]]

  type QuasiApplicative2[F[_, _]] = QuasiApplicative[F[Throwable, ?]]
  type QuasiApplicative3[F[_, _, _]] = QuasiApplicative[F[Any, Throwable, ?]]

  type QuasiEffectAsync2[F[_, _]] = QuasiAsync[F[Throwable, ?]]
  type QuasiEffectAsync3[F[_, _, _]] = QuasiAsync[F[Any, Throwable, ?]]

  type QuasiEffectRunner2[F[_, _]] = QuasiEffectRunner[F[Throwable, ?]]
  type QuasiEffectRunner3[F[_, _, _]] = QuasiEffectRunner[F[Any, Throwable, ?]]

  @deprecated("Renamed to QuasiEffect", "1.0")
  type DIEffect[F[_]] = QuasiEffect[F]
  @deprecated("Renamed to QuasiEffect2", "1.0")
  type DIEffect2[F[_, _]] = QuasiEffect[F[Throwable, ?]]
  @deprecated("Renamed to QuasiEffect3", "1.0")
  type DIEffect3[F[_, _, _]] = QuasiEffect[F[Any, Throwable, ?]]

  @deprecated("Renamed to QuasiApplicative", "1.0")
  type DIApplicative[F[_]] = QuasiApplicative[F]
  @deprecated("Renamed to QuasiApplicative2", "1.0")
  type DIApplicative2[F[_, _]] = QuasiApplicative[F[Throwable, ?]]
  @deprecated("Renamed to QuasiApplicative3", "1.0")
  type DIApplicative3[F[_, _, _]] = QuasiApplicative[F[Any, Throwable, ?]]

  @deprecated("Renamed to QuasiAsync", "1.0")
  type DIEffectAsync[F[_]] = QuasiAsync[F]
  @deprecated("Renamed to QuasiAsync2", "1.0")
  type DIEffectAsync2[F[_, _]] = QuasiAsync[F[Throwable, ?]]
  @deprecated("Renamed to QuasiAsync3", "1.0")
  type DIEffectAsync3[F[_, _, _]] = QuasiAsync[F[Any, Throwable, ?]]

  @deprecated("Renamed to QuasiEffectRunner", "1.0")
  type DIEffectRunner[F[_]] = QuasiEffectRunner[F]
  @deprecated("Renamed to QuasiEffectRunner2", "1.0")
  type DIEffectRunner2[F[_, _]] = QuasiEffectRunner[F[Throwable, ?]]
  @deprecated("Renamed to QuasiEffectRunner3", "1.0")
  type DIEffectRunner3[F[_, _, _]] = QuasiEffectRunner[F[Any, Throwable, ?]]
}
