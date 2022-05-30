package izumi.functional.bio.data

import scala.language.implicitConversions

/**
  * @note if you're using Scala 2.12 and getting "no such method" or implicit-related errors when interacting with Morphism1,
  * you must enable `-Xsource:2.13` or `-Xsource:3` compiler option.
  *
  * BIO does not work without `-Xsource:2.13` or `-Xsource:3` option on 2.12.
  */
object Morphism1 {
  private[data] type Morphism1[-F[_], +G[_]] = Morphism3[Lambda[(R, E, A) => F[A]], Lambda[(R, E, A) => G[A]]]

  @inline def apply[F[_], G[_]](polyFunction: F[UnknownA] => G[UnknownA]): Morphism1[F, G] = polyFunction.asInstanceOf[Morphism1[F, G]]

  @inline def identity[F[_]]: Morphism1[F, F] = Morphism3.identity1

  /**
    * When it's more convenient to write a polymorphic function using a class (or kind-projector's lambda syntax):
    *
    * {{{
    *   Lambda[FunctionK.Instance[F, G]](a => f(b(a)))
    * }}}
    */
  trait Instance[-F[_], +G[_]] {
    def apply[A](fa: F[A]): G[A]

    @inline final def compose[F1[_]](f: Morphism1[F1, F]): Morphism1[F1, G] = f.andThen(this)
    @inline final def andThen[H[_]](f: Morphism1[G, H]): Morphism1[F, H] = f.compose(this)
  }
  object Instance {
    @inline implicit def asMorphism[F[_], G[_]](functionKKInstance: Morphism1.Instance[F, G]): Morphism1[F, G] =
      Morphism1(functionKKInstance.apply)
  }

  private[Morphism1] type UnknownA

}
