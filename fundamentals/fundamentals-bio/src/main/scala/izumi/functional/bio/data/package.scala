package izumi.functional.bio

package object data {
  type ~>[-F[_], +G[_]] = Morphism1[F, G]
  @deprecated("Prefer alphabetic object name (`Morphism1`)", "always")
  lazy val ~> : Morphism1.type = Morphism1

  type ~>>[-F[_, _], +G[_, _]] = Morphism2[F, G]
  @deprecated("Prefer alphabetic object name (`Morphism2`)", "always")
  lazy val ~>> : Morphism2.type = Morphism2

  type ~>>>[-F[_, _, _], +G[_, _, _]] = Morphism3[F, G]
  @deprecated("Prefer alphabetic object name (`Morphism2`)", "always")
  lazy val ~>>> : Morphism3.type = Morphism3

  type Morphism1[-F[_], +G[_]] = Morphism1.Morphism1[F, G]
  type Morphism2[-F[_, _], +G[_, _]] = Morphism2.Morphism2[F, G]
  type Morphism3[-F[_, _, _], +G[_, _, _]] = Morphism3.Morphism3[F, G]
}
