package izumi.functional.bio.data

trait Isomorphism1[F[_], G[_]] {
  def to: F ~> G
  def from: G ~> F
}

object Isomorphism1 {
  @inline def apply[F[_], G[_]](implicit iso: Isomorphism1[F, G]): Isomorphism1[F, G] = iso

  implicit def identity[F[_]]: Isomorphism1[F, F] = new Isomorphism1[F, F] {
    override def to: F ~> F = Morphism1.identity
    override def from: F ~> F = Morphism1.identity
  }
}
