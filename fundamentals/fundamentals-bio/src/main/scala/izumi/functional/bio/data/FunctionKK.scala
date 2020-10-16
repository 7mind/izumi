package izumi.functional.bio.data

import scala.language.implicitConversions

/**
  * Note: if you're using Scala 2.12 and getting "no such method" or implicit-related errors when interacting with FunctionKK,
  * you must enable `-Xsource:2.13` compiler option. BIO does not work without this option on 2.12.
  */
object FunctionKK {
  private[data] type FunctionKK[-F[_, _], +G[_, _]]

  @inline def apply[F[_, _], G[_, _]](polyFunction: F[UnknownE, UnknownA] => G[UnknownE, UnknownA]): FunctionKK[F, G] = polyFunction.asInstanceOf[FunctionKK[F, G]]

  @inline def id[F[_, _]]: FunctionKK[F, F] = (identity[Any] _).asInstanceOf[FunctionKK[F, F]]

  implicit final class FunctionKKOps[-F[_, _], +G[_, _]](private val self: FunctionKK[F, G]) extends AnyVal {
    @inline def apply[E, A](f: F[E, A]): G[E, A] = self.asInstanceOf[F[E, A] => G[E, A]](f)

    @inline def compose[F1[_, _]](f: FunctionKK[F1, F]): FunctionKK[F1, G] = FunctionKK(g => apply(f(g)))
    @inline def andThen[H[_, _]](f: FunctionKK[G, H]): FunctionKK[F, H] = f.compose(self)
  }

  /**
    * When it's more convenient to write a polymorphic function using a class or kind-projector's lambda syntax:
    *
    * {{{
    *   Lambda[FunctionKK.Instance[F, G]](a => f(b(a)))
    * }}}
    */
  trait Instance[-F[_, _], +G[_, _]] { self =>
    def apply[E, A](fa: F[E, A]): G[E, A]

    @inline final def compose[F1[_, _]](f: FunctionKK[F1, F]): FunctionKK[F1, G] = f.andThen(this)
    @inline final def andThen[H[_, _]](f: FunctionKK[G, H]): FunctionKK[F, H] = f.compose(this)
  }
  object Instance {
    @inline implicit def asFunctionKK[F[_, _], G[_, _]](functionKKInstance: FunctionKK.Instance[F, G]): FunctionKK[F, G] =
      FunctionKK(functionKKInstance.apply)
  }

  private[FunctionKK] type UnknownE
  private[FunctionKK] type UnknownA
}
