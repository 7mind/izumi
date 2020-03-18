package izumi.distage.model.effect

import izumi.functional.bio.BIOLocal

trait DIRunReader[F[_, _]] {
  type G[A]
  def provide[R, A](fa: F[R, A])(env: R): G[A]
}

object DIRunReader {
  type Aux[F[_, _], G0[_]] = DIRunReader[F] { type G[A] = G0[A] }

//  implicit def KleisliDIRunReader[F[_]]

  implicit def BIOLocalDIRunReader[F[-_, +_, +_]: BIOLocal, E]: DIRunReader.Aux[F[?, E, ?], F[Any, E, ?]] = new DIRunReader[F[?, E, ?]] {
    override type G[A] =  F[Any, E, A]
    override def provide[R, A](fa: F[R, E, A])(env: R): F[Any, E, A] = fa.provide(env)
  }
}
