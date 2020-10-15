package izumi.distage.model

package object effect {
  type QuasiIO2[F[_, _]] = QuasiIO[F[Throwable, ?]]
  type QuasiIO3[F[_, _, _]] = QuasiIO[F[Any, Throwable, ?]]

  type QuasiApplicative2[F[_, _]] = QuasiApplicative[F[Throwable, ?]]
  type QuasiApplicative3[F[_, _, _]] = QuasiApplicative[F[Any, Throwable, ?]]

  type QuasiAsync2[F[_, _]] = QuasiAsync[F[Throwable, ?]]
  type QuasiAsync3[F[_, _, _]] = QuasiAsync[F[Any, Throwable, ?]]

  type QuasiIORunner2[F[_, _]] = QuasiIORunner[F[Throwable, ?]]
  type QuasiIORunner3[F[_, _, _]] = QuasiIORunner[F[Any, Throwable, ?]]

  @deprecated("Renamed to QuasiIO", "1.0")
  type DIEffect[F[_]] = QuasiIO[F]
  @deprecated("Renamed to QuasiIO2", "1.0")
  type DIEffect2[F[_, _]] = QuasiIO[F[Throwable, ?]]
  @deprecated("Renamed to QuasiIO3", "1.0")
  type DIEffect3[F[_, _, _]] = QuasiIO[F[Any, Throwable, ?]]

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

  @deprecated("Renamed to QuasiIORunner", "1.0")
  type DIEffectRunner[F[_]] = QuasiIORunner[F]
  @deprecated("Renamed to QuasiIORunner2", "1.0")
  type DIEffectRunner2[F[_, _]] = QuasiIORunner[F[Throwable, ?]]
  @deprecated("Renamed to QuasiIORunner3", "1.0")
  type DIEffectRunner3[F[_, _, _]] = QuasiIORunner[F[Any, Throwable, ?]]
}
