package izumi.functional.bio.data

import scala.language.implicitConversions

object FunctionKK {
  private[data] type FunctionKK[-F[_, _], +G[_, _]] <: (Any { type xa }) with TagFor212ImplicitScope

  @inline def apply[F[_, _], G[_, _]](polyFunction: F[UnknownE, UnknownA] => G[UnknownE, UnknownA]): FunctionKK[F, G] = polyFunction.asInstanceOf[FunctionKK[F, G]]

  implicit final class FunctionKKOps[-F[_, _], +G[_, _]](private val self: FunctionKK[F, G]) extends AnyVal {
    @inline def apply[E, A](f: F[E, A]): G[E, A] = self.asInstanceOf[F[E, A] => G[E, A]](f)

    @inline def compose[F1[_, _]](f: FunctionKK[F1, F]): FunctionKK[F1, G] = FunctionKK(g => apply(f(g)))
    @inline def andThen[H[_, _]](f: FunctionKK[G, H]): FunctionKK[F, H] = f.compose(self)
  }

  @inline def id[F[_, _]]: FunctionKK[F, F] = (identity[Any] _).asInstanceOf[FunctionKK[F, F]]

  /**
    * When it's more convenient to write a polymorphic function using a class or kind-projector's lambda syntax:
    *
    * {{{
    *   Lambda[FunctionKK.Instance[F, G]](a => f(b(a)))
    * }}}
    */
  trait Instance[-F[_, _], +G[_, _]] { self =>
    def apply[E, A](fa: F[E, A]): G[E, A]

    @inline final def compose[F1[_, _]](f: FunctionKK[F1, F]): FunctionKK[F1, G] = f.andThen(self)
    @inline final def andThen[H[_, _]](f: FunctionKK[G, H]): FunctionKK[F, H] = f.compose(self)
  }
  object Instance {
    @inline implicit def asFunctionKK[F[_, _], G[_, _]](functionKKInstance: FunctionKK.Instance[F, G]): FunctionKK[F, G] =
      FunctionKK(functionKKInstance.apply)
  }

  /**
    * 2.12 compat. In 2.13+ abstract type aliases receive the implicit scope of their enclosing object on their own.
    * But in 2.12 only *Class Types* do. So, here's a Class Type
    */
  private[FunctionKK] final abstract class TagFor212ImplicitScope

  private[FunctionKK] type UnknownE
  private[FunctionKK] type UnknownA
}
