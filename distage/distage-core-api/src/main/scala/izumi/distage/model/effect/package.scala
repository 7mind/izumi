package izumi.distage.model

package object effect {
  type QuasiIO2[F[_, _]] = QuasiIO[F[Throwable, _]]
  type QuasiIO3[F[_, _, _]] = QuasiIO[F[Any, Throwable, _]]

  type QuasiApplicative2[F[_, _]] = QuasiApplicative[F[Throwable, _]]
  type QuasiApplicative3[F[_, _, _]] = QuasiApplicative[F[Any, Throwable, _]]

  type QuasiAsync2[F[_, _]] = QuasiAsync[F[Throwable, _]]
  type QuasiAsync3[F[_, _, _]] = QuasiAsync[F[Any, Throwable, _]]

  type QuasiIORunner2[F[_, _]] = QuasiIORunner[F[Throwable, _]]
  type QuasiIORunner3[F[_, _, _]] = QuasiIORunner[F[Any, Throwable, _]]
}
