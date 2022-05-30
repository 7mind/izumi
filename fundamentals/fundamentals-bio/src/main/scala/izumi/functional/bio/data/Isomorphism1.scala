package izumi.functional.bio.data

object Isomorphism1 {
  @inline def apply[F[_], G[_]](implicit iso: Isomorphism1[F, G]): Isomorphism1[F, G] = iso

  @inline def apply[F[_], G[_]](to: F ~> G, from: G ~> F): Isomorphism1[F, G] = Isomorphism3(to, from)
}
