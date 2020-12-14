package izumi.functional.bio.data

trait Isomorphism3[F[_, _, _], G[_, _, _]] {
  def to: F ~>>> G
  def from: G ~>>> F
}

object Isomorphism3 {
  @inline def apply[F[_, _, _], G[_, _, _]](implicit iso: Isomorphism3[F, G]): Isomorphism3[F, G] = iso

  implicit def identity[F[_, _, _]]: Isomorphism3[F, F] = new Isomorphism3[F, F] {
    override def to: F ~>>> F = Morphism3.identity
    override def from: F ~>>> F = Morphism3.identity
  }
}
