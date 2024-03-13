package izumi.functional.bio.data

trait Isomorphism3[F[_, _, _], G[_, _, _]] {
  def to: F ~>>> G
  def from: G ~>>> F
}

object Isomorphism3 {
  @inline def apply[F[_, _, _], G[_, _, _]](implicit iso: Isomorphism3[F, G]): Isomorphism3[F, G] = iso

  def apply[F[_, _, _], G[_, _, _]](to: F ~>>> G, from: G ~>>> F): Isomorphism3[F, G] = {
    final case class IsomorphismImpl(to: F ~>>> G, from: G ~>>> F) extends Isomorphism3[F, G]
    IsomorphismImpl(to, from)
  }

  @inline implicit def identity3[F[_, _, _]]: Isomorphism3[F, F] = Isomorphism3(Morphism3.identity, Morphism3.identity)
}

sealed trait LowPriorityIsomorphismInstances extends LowPriorityIsomorphismInstances1 {
  @inline implicit def identity2[F[_, _]]: Isomorphism2[F, F] = Isomorphism2(Morphism2.identity, Morphism2.identity)
}

sealed trait LowPriorityIsomorphismInstances1 {
  @inline implicit def identity1[F[_]]: Isomorphism1[F, F] = Isomorphism1(Morphism1.identity, Morphism1.identity)
}
