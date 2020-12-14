package izumi.functional.bio.data

import scala.language.implicitConversions

/**
  * @note if you're using Scala 2.12 and getting "no such method" or implicit-related errors when interacting with Morphism2,
  * you must enable `-Xsource:2.13` compiler option. BIO does not work without this option on 2.12.
  */
object Morphism2 {
  private[data] type Morphism2[-F[_, _], +G[_, _]]

  @inline def apply[F[_, _], G[_, _]](polyFunction: F[UnknownE, UnknownA] => G[UnknownE, UnknownA]): Morphism2[F, G] = polyFunction.asInstanceOf[Morphism2[F, G]]

  @inline implicit def identity[F[_, _]]: Morphism2[F, F] = (Predef.identity[Any] _).asInstanceOf[Morphism2[F, F]]

  implicit final class Ops[-F[_, _], +G[_, _]](private val self: Morphism2[F, G]) extends AnyVal {
    @inline def apply[E, A](f: F[E, A]): G[E, A] = self.asInstanceOf[F[E, A] => G[E, A]](f)

    @inline def compose[F1[_, _]](f: Morphism2[F1, F]): Morphism2[F1, G] = Morphism2(g => apply(f(g)))
    @inline def andThen[H[_, _]](f: Morphism2[G, H]): Morphism2[F, H] = f.compose(self)
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

    @inline final def compose[F1[_, _]](f: Morphism2[F1, F]): Morphism2[F1, G] = f.andThen(this)
    @inline final def andThen[H[_, _]](f: Morphism2[G, H]): Morphism2[F, H] = f.compose(this)
  }
  object Instance {
    @inline implicit def asMorphism[F[_, _], G[_, _]](functionKKInstance: Morphism2.Instance[F, G]): Morphism2[F, G] =
      Morphism2(functionKKInstance.apply)
  }

  implicit def conversion3To2[F[_, _, _], G[_, _, _], R](f: Morphism3[F, G]): Morphism2[F[R, ?, ?], G[R, ?, ?]] = f.asInstanceOf[Morphism2[F[R, ?, ?], G[R, ?, ?]]]
  implicit def Convert3To2[F[_, _, _], G[_, _, _], R](implicit f: Morphism3[F, G]): Morphism2[F[R, ?, ?], G[R, ?, ?]] = f.asInstanceOf[Morphism2[F[R, ?, ?], G[R, ?, ?]]]

  private[Morphism2] type UnknownE
  private[Morphism2] type UnknownA

}
