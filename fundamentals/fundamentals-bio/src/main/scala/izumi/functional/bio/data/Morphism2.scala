package izumi.functional.bio.data

import scala.language.implicitConversions

/**
  * @note if you're using Scala 2.12 and getting "no such method" or implicit-related errors when interacting with Morphism2,
  * you must enable `-Xsource:2.13` or `-Xsource:3` compiler option.
  *
  * BIO does not work without `-Xsource:2.13` or `-Xsource:3` option on 2.12.
  */
object Morphism2 {
  private[data] type Morphism2[-F[_, _], +G[_, _]] = Morphism3[Lambda[(R, E, A) => F[E, A]], Lambda[(R, E, A) => G[E, A]]]

  @inline def apply[F[_, _], G[_, _]](polyFunction: F[UnknownE, UnknownA] => G[UnknownE, UnknownA]): Morphism2[F, G] = polyFunction.asInstanceOf[Morphism2[F, G]]

  @inline def identity[F[_, _]]: Morphism2[F, F] = Morphism3.identity2

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

  private[Morphism2] type UnknownE
  private[Morphism2] type UnknownA
}
