package izumi.functional.bio.data

import scala.language.implicitConversions

/**
  * @note if you're using Scala 2.12 and getting "no such method" or implicit-related errors when interacting with Morphism3,
  * you must enable `-Xsource:2.13` or `-Xsource:3` compiler option.
  *
  * BIO does not work without `-Xsource:2.13` or `-Xsource:3` option on 2.12.
  */
object Morphism3 extends LowPriorityMorphismInstances {
  protected[data] type Morphism3[-F[_, _, _], +G[_, _, _]] // Note: protected[data] instead of private[data] due to a 2.12 bug causing build to fail
  implicit final class Ops[-F[_, _, _], +G[_, _, _]](private val self: Morphism3[F, G]) extends AnyVal {
    @inline def apply[R, E, A](f: F[R, E, A]): G[R, E, A] = self.asInstanceOf[F[R, E, A] => G[R, E, A]](f)

    @inline def compose[F1[_, _, _]](f: Morphism3[F1, F]): Morphism3[F1, G] = Morphism3(g => apply(f(g)))
    @inline def andThen[H[_, _, _]](f: Morphism3[G, H]): Morphism3[F, H] = f.compose(self)
  }

  implicit final class SyntaxToCats[F[_], G[_]](private val self: Morphism1[F, G]) extends AnyVal {
    @inline def toCats: cats.arrow.FunctionK[F, G] = Lambda[cats.arrow.FunctionK[F, G]](self(_))
  }

  @inline def apply[F[_, _, _], G[_, _, _]](polyFunction: F[UnknownR, UnknownE, UnknownA] => G[UnknownR, UnknownE, UnknownA]): Morphism3[F, G] = {
    polyFunction.asInstanceOf[Morphism3[F, G]]
  }

  @inline def identity[F[_, _, _]]: Morphism3[F, F] = Morphism3(Predef.identity)

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

  @inline implicit def fromCats[F[_], G[_]](fn: cats.arrow.FunctionK[F, G]): Morphism1[F, G] = Morphism1(fn(_))

  /** If requested implicitly, an identity morphism is always available */
  @inline implicit def identity1[F[_]]: Morphism1[F, F] = Morphism3.identity

  implicit def conversion3To2[F[_, _, _], G[_, _, _], R](f: Morphism3[F, G]): Morphism2[F[R, _, _], G[R, _, _]] = f.asInstanceOf[Morphism2[F[R, _, _], G[R, _, _]]]

  implicit def conversion2To1[F[_, _], G[_, _], E](f: Morphism2[F, G]): Morphism1[F[E, _], G[E, _]] = f.asInstanceOf[Morphism1[F[E, _], G[E, _]]]
  implicit def conversion2To1Nothing[F[_, _], G[_, _]](f: Morphism2[F, G]): Morphism1[F[Nothing, _], G[Nothing, _]] =
    f.asInstanceOf[Morphism1[F[Nothing, _], G[Nothing, _]]] // workaround for inference issues with `E=Nothing`

  implicit def Convert3To2[F[_, _, _], G[_, _, _], R](implicit f: Morphism3[F, G]): Morphism2[F[R, _, _], G[R, _, _]] = f.asInstanceOf[Morphism2[F[R, _, _], G[R, _, _]]]

  implicit def Convert2To1[F[_, _], G[_, _], E](implicit f: Morphism2[F, G]): Morphism1[F[E, _], G[E, _]] = f.asInstanceOf[Morphism1[F[E, _], G[E, _]]]
  implicit def Convert2To1Nothing[F[_, _], G[_, _]](implicit f: Morphism2[F, G]): Morphism1[F[Nothing, _], G[Nothing, _]] =
    f.asInstanceOf[Morphism1[F[Nothing, _], G[Nothing, _]]] // workaround for inference issues with `E=Nothing`

  private[Morphism3] type UnknownR
  private[Morphism3] type UnknownE
  private[Morphism3] type UnknownA
}

sealed trait LowPriorityMorphismInstances extends LowPriorityMorphismInstances1 {
  @inline implicit def identity2[F[_, _]]: Morphism2[F, F] = Morphism3.identity
}

sealed trait LowPriorityMorphismInstances1 {
  @inline implicit def identity3[F[_, _, _]]: Morphism3[F, F] = Morphism3.identity
}
