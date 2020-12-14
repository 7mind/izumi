package izumi.functional.bio.data

trait Isomorphism2[F[_, _], G[_, _]] {
  def to: F ~>> G
  def from: G ~>> F
}

object Isomorphism2 {
  @inline def apply[F[_, _], G[_, _]](implicit iso: Isomorphism2[F, G]): Isomorphism2[F, G] = iso

  implicit def identity[F[_, _]]: Isomorphism2[F, F] = new Isomorphism2[F, F] {
    override def to: F ~>> F = Morphism2.identity
    override def from: F ~>> F = Morphism2.identity
  }
}
