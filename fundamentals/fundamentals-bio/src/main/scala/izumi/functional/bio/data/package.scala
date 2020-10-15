package izumi.functional.bio

package object data {
  type ~>[-F[_], +G[_]] = FunctionK[F, G]
  type ~>>[-F[_, _], +G[_, _]] = FunctionKK[F, G]

  type FunctionK[-F[_], +G[_]] = FunctionK.FunctionK[F, G]
  type FunctionKK[-F[_, _], +G[_, _]] = FunctionKK.FunctionKK[F, G]
}
