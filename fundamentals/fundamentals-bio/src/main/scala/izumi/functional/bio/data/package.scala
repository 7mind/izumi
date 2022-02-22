package izumi.functional.bio

package object data {
  type ~>[-F[_], +G[_]] = Morphism1.Morphism1[F, G]
  type ~>>[-F[_, _], +G[_, _]] = Morphism2.Morphism2[F, G]
  type ~>>>[-F[_, _, _], +G[_, _, _]] = Morphism3.Morphism3[F, G]

  type Morphism1[-F[_], +G[_]] = Morphism1.Morphism1[F, G]
  type Morphism2[-F[_, _], +G[_, _]] = Morphism2.Morphism2[F, G]
  type Morphism3[-F[_, _, _], +G[_, _, _]] = Morphism3.Morphism3[F, G]

  type RestoreInterruption2[F[_, _]] = Morphism2.Morphism2[F, F]
  type RestoreInterruption3[F[_, _, _]] = Morphism3.Morphism3[F, F]
}
