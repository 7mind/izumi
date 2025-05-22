package izumi.functional.bio.data

object Isomorphism2 {
  @inline def apply[F[_, _], G[_, _]](using iso: Isomorphism2[F, G]): Isomorphism2[F, G] = iso

  @inline def apply[F[_, _], G[_, _]](to: F ~>> G, from: G ~>> F): Isomorphism2[F, G] = Isomorphism3(to, from)
}
