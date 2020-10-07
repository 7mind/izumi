package izumi.functional.bio

package object data {
  type ~>>[F[_, _], G[_, _]] = FunctionKK[F, G]
}
