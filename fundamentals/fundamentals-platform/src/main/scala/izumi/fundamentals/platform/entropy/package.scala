package izumi.fundamentals.platform

package object entropy {
  type Entropy2[F[_, _]] = Entropy[F[Nothing, ?]]

  object Entropy2 {
    def apply[F[_, _]: Entropy2]: Entropy2[F] = implicitly
  }
}
