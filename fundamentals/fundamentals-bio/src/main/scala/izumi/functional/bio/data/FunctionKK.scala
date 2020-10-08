package izumi.functional.bio.data

trait FunctionKK[F[_, _], G[_, _]] extends Serializable { self =>
  def apply[E, A](fa: F[E, A]): G[E, A]

  def compose[F1[_, _]](f: FunctionKK[F1, F]): FunctionKK[F1, G] =
    new FunctionKK[F1, G] { def apply[E, A](fa: F1[E, A]): G[E, A] = self(f(fa)) }

  def andThen[H[_, _]](f: FunctionKK[G, H]): FunctionKK[F, H] =
    f.compose(self)
}

object FunctionKK {
  def id[F[_, _]]: FunctionKK[F, F] = new FunctionKK[F, F] { def apply[E, A](fa: F[E, A]): F[E, A] = fa }

  private[FunctionKK] type UnknownE
  private[FunctionKK] type UnknownA
  def apply[F[_, _], G[_, _]](transform: (F[UnknownE, UnknownA] => G[UnknownE, UnknownA])): FunctionKK[F, G] = {
    new ~>>[F, G] { override def apply[E, A](fa: F[E, A]): G[E, A] = transform.asInstanceOf[F[E, A] => G[E, A]](fa) }
  }
}
