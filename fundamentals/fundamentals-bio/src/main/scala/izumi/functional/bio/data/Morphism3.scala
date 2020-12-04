package izumi.functional.bio.data

import scala.language.implicitConversions

/**
  * @note if you're using Scala 2.12 and getting "no such method" or implicit-related errors when interacting with Morphism3,
  * you must enable `-Xsource:2.13` compiler option. BIO does not work without this option on 2.12.
  */
object Morphism3 {
  private[data] type Morphism3[-F[_, _, _], +G[_, _, _]]

  @inline def apply[F[_, _, _], G[_, _, _]](polyFunction: F[UnknownR, UnknownE, UnknownA] => G[UnknownR, UnknownE, UnknownA]): Morphism3[F, G] =
    polyFunction.asInstanceOf[Morphism3[F, G]]

  @inline implicit def identity[F[_, _, _]]: Morphism3[F, F] = (Predef.identity[Any] _).asInstanceOf[Morphism3[F, F]]

  implicit final class Ops[-F[_, _, _], +G[_, _, _]](private val self: Morphism3[F, G]) extends AnyVal {
    @inline def apply[R, E, A](f: F[R, E, A]): G[R, E, A] = self.asInstanceOf[F[R, E, A] => G[R, E, A]](f)

    @inline def compose[F1[_, _, _]](f: Morphism3[F1, F]): Morphism3[F1, G] = Morphism3(g => apply(f(g)))
    @inline def andThen[H[_, _, _]](f: Morphism3[G, H]): Morphism3[F, H] = f.compose(self)
  }

  /**
    * When it's more convenient to write a polymorphic function using a class or kind-projector's lambda syntax:
    *
    * {{{
    *   Lambda[FunctionKK.Instance[F, G]](a => f(b(a)))
    * }}}
    */
  trait Instance[-F[_, _, _], +G[_, _, _]] { self =>
    def apply[R, E, A](fa: F[R, E, A]): G[R, E, A]

    @inline final def compose[F1[_, _, _]](f: Morphism3[F1, F]): Morphism3[F1, G] = f.andThen(this)
    @inline final def andThen[H[_, _, _]](f: Morphism3[G, H]): Morphism3[F, H] = f.compose(this)
  }
  object Instance {
    @inline implicit def asMorphism[F[_, _, _], G[_, _, _]](functionKKInstance: Morphism3.Instance[F, G]): Morphism3[F, G] =
      Morphism3(functionKKInstance.apply)
  }

  private[Morphism3] type UnknownR
  private[Morphism3] type UnknownE
  private[Morphism3] type UnknownA
}
