package izumi.functional.bio.data

import scala.language.implicitConversions

/**
  * @note if you're using Scala 2.12 and getting "no such method" or implicit-related errors when interacting with Morphism1,
  * you must enable `-Xsource:2.13` compiler option. BIO does not work without this option on 2.12.
  */
object Morphism1 {
  private[data] type Morphism1[-F[_], +G[_]]

  @inline def apply[F[_], G[_]](polyFunction: F[UnknownA] => G[UnknownA]): Morphism1[F, G] = polyFunction.asInstanceOf[Morphism1[F, G]]

  @inline implicit def identity[F[_]]: Morphism1[F, F] = (Predef.identity[Any] _).asInstanceOf[Morphism1[F, F]]

  implicit final class Ops[-F[_], +G[_]](private val self: Morphism1[F, G]) extends AnyVal {
    @inline def apply[A](f: F[A]): G[A] = self.asInstanceOf[F[A] => G[A]](f)

    @inline def compose[F1[_]](f: Morphism1[F1, F]): Morphism1[F1, G] = Morphism1(g => apply(f(g)))
    @inline def andThen[H[_]](f: Morphism1[G, H]): Morphism1[F, H] = f.compose(self)
  }

  @inline implicit def fromCats[F[_], G[_]](fn: cats.arrow.FunctionK[F, G]): Morphism1[F, G] = Morphism1(fn(_))

  implicit final class SyntaxToCats[F[_], G[_]](private val self: Morphism1[F, G]) extends AnyVal {
    @inline def toCats: cats.arrow.FunctionK[F, G] = Lambda[cats.arrow.FunctionK[F, G]](self(_))
  }

  /**
    * When it's more convenient to write a polymorphic function using a class or kind-projector's lambda syntax:
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

  implicit def conversion2To1[F[_, _], G[_, _], E](f: Morphism2[F, G]): Morphism1[F[E, ?], G[E, ?]] = f.asInstanceOf[Morphism1[F[E, ?], G[E, ?]]]
  implicit def Convert2To1[F[_, _], G[_, _], E](implicit f: Morphism2[F, G]): Morphism1[F[E, ?], G[E, ?]] = f.asInstanceOf[Morphism1[F[E, ?], G[E, ?]]]

  // workaround for inference issues with `E=Nothing`
  implicit def conversion2To1Nothing[F[_, _], G[_, _]](f: Morphism2[F, G]): Morphism1[F[Nothing, ?], G[Nothing, ?]] =
    f.asInstanceOf[Morphism1[F[Nothing, ?], G[Nothing, ?]]]
  implicit def Convert2To1Nothing[F[_, _], G[_, _]](implicit f: Morphism2[F, G]): Morphism1[F[Nothing, ?], G[Nothing, ?]] =
    f.asInstanceOf[Morphism1[F[Nothing, ?], G[Nothing, ?]]]

  private[Morphism1] type UnknownA

}
