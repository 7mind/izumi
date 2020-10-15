package izumi.functional.bio.data

import scala.language.implicitConversions

object FunctionK {
  private[data] type FunctionK[-F[_], +G[_]] <: (Any { type xa }) with TagFor212ImplicitScope

  @inline def apply[F[_], G[_]](polyFunction: F[UnknownA] => G[UnknownA]): FunctionK[F, G] = polyFunction.asInstanceOf[FunctionK[F, G]]

  implicit final class FunctionKOps[-F[_], +G[_]](private val self: FunctionK[F, G]) extends AnyVal {
    @inline def apply[A](f: F[A]): G[A] = self.asInstanceOf[F[A] => G[A]](f)

    @inline def compose[F1[_]](f: FunctionK[F1, F]): FunctionK[F1, G] = FunctionK(g => apply(f(g)))
    @inline def andThen[H[_]](f: FunctionK[G, H]): FunctionK[F, H] = f.compose(self)
  }

  @inline def id[F[_]]: FunctionK[F, F] = (identity[Any] _).asInstanceOf[FunctionK[F, F]]

  /**
    * When it's more convenient to write a polymorphic function using a class or kind-projector's lambda syntax:
    *
    * {{{
    *   Lambda[FunctionK.Instance[F, G]](a => f(b(a)))
    * }}}
    */
  trait Instance[-F[_], +G[_]] { self =>
    def apply[A](fa: F[A]): G[A]

    @inline final def compose[F1[_]](f: FunctionK[F1, F]): FunctionK[F1, G] = f.andThen(self)
    @inline final def andThen[H[_]](f: FunctionK[G, H]): FunctionK[F, H] = f.compose(self)
  }
  object Instance {
    @inline implicit def asFunctionK[F[_], G[_]](functionKKInstance: FunctionK.Instance[F, G]): FunctionK[F, G] =
      FunctionK(functionKKInstance.apply)
  }

  /**
    * 2.12 compat. In 2.13+ abstract type aliases receive the implicit scope of their enclosing object on their own.
    * But in 2.12 only *Class Types* do. So, here's a Class Type
    */
  private[FunctionK] final abstract class TagFor212ImplicitScope

  private[FunctionK] type UnknownA
}
