package izumi.functional.bio

import izumi.functional.bio.BIOImplicitPuns.BIOImplicitPuns1

import scala.language.implicitConversions

trait BIOImplicitPuns extends BIOImplicitPuns1 {
  @inline implicit final def BIOAsync[F[_, +_]: BIOFunctor, E, A](self: F[E, A]): BIOSyntax.BIOFunctorOps[F, E, A] = new BIOSyntax.BIOFunctorOps[F, E, A](self)
  @inline implicit final def BIOAsync[F[+_, +_]: BIOBifunctor, E, A](self: F[E, A]): BIOSyntax.BIOBifunctorOps[F, E, A] = new BIOSyntax.BIOBifunctorOps[F, E, A](self)
  @inline implicit final def BIOAsync[F[+_, +_]: BIOApplicative, E, A](self: F[E, A]): BIOSyntax.BIOApplicativeOps[F, E, A] = new BIOSyntax.BIOApplicativeOps[F, E, A](self)
  @inline implicit final def BIOAsync[F[+_, +_]: BIOMonad, E, A](self: F[E, A]): BIOSyntax.BIOMonadOps[F, E, A] = new BIOSyntax.BIOMonadOps[F, E, A](self)
  @inline implicit final def BIOAsync[F[+_, +_]: BIOGuarantee, E, A](self: F[E, A]): BIOSyntax.BIOGuaranteeOps[F, E, A] = new BIOSyntax.BIOGuaranteeOps[F, E, A](self)
  @inline implicit final def BIOAsync[F[+_, +_]: BIOError, E, A](self: F[E, A]): BIOSyntax.BIOErrorOps[F, E, A] = new BIOSyntax.BIOErrorOps[F, E, A](self)
  @inline implicit final def BIOAsync[F[+_, +_]: BIOMonadError, E, A](self: F[E, A]): BIOSyntax.BIOMonadErrorOps[F, E, A] = new BIOSyntax.BIOMonadErrorOps[F, E, A](self)
  @inline implicit final def BIOAsync[F[+_, +_]: BIOBracket, E, A](self: F[E, A]): BIOSyntax.BIOBracketOps[F, E, A] = new BIOSyntax.BIOBracketOps[F, E, A](self)
  @inline implicit final def BIOAsync[F[+_, +_]: BIOPanic, E, A](self: F[E, A]): BIOSyntax.BIOPanicOps[F, E, A] = new BIOSyntax.BIOPanicOps[F, E, A](self)
  @inline implicit final def BIOAsync[F[+_, +_]: BIO, E, A](self: F[E, A]): BIOSyntax.BIOOps[F, E, A] = new BIOSyntax.BIOOps[F, E, A](self)
  @inline implicit final def BIOAsync[F[+_, +_]: BIOAsync, E, A](self: F[E, A]): BIOSyntax.BIOAsyncOps[F, E, A] = new BIOSyntax.BIOAsyncOps[F, E, A](self)
  @inline implicit final def BIOAsync[F[+_, +_]: BIOMonad, E, A](self: F[E, F[E, A]])(implicit dummy: DummyImplicit): BIOSyntax.BIOFlattenOps[F, E, A] = new BIOSyntax.BIOFlattenOps[F, E, A](self)

  @inline implicit final def BIOFork[F[_, _]: BIOFork, E, A](self: F[E, A]): BIOSyntax.BIOForkOps[F, E, A] = new BIOSyntax.BIOForkOps[F, E, A](self)

  @inline final def BIOAsync[F[+_, +_]: BIOAsync]: BIOAsync[F] = implicitly
  @inline final def BIOFork[F[_, _]: BIOFork]: BIOFork[F] = implicitly
}

object BIOImplicitPuns {

  trait BIOImplicitPuns1 extends BIOImplicitPuns2 {
    /**
     * Shorthand for [[BIO#syncThrowable]]
     *
     * {{{
     *   BIO(println("Hello world!"))
     * }}}
     * */
    @inline final def BIO[F[+_, +_], A](effect: => A)(implicit F: BIO[F]): F[Throwable, A] = F.syncThrowable(effect)
    @inline final def BIO[F[+_, +_]: BIO]: BIO[F] = implicitly

    @inline implicit final def BIO[F[_, +_]: BIOFunctor, E, A](self: F[E, A]): BIOSyntax.BIOFunctorOps[F, E, A] = new BIOSyntax.BIOFunctorOps[F, E, A](self)
    @inline implicit final def BIO[F[+_, +_]: BIOBifunctor, E, A](self: F[E, A]): BIOSyntax.BIOBifunctorOps[F, E, A] = new BIOSyntax.BIOBifunctorOps[F, E, A](self)
    @inline implicit final def BIO[F[+_, +_]: BIOApplicative, E, A](self: F[E, A]): BIOSyntax.BIOApplicativeOps[F, E, A] = new BIOSyntax.BIOApplicativeOps[F, E, A](self)
    @inline implicit final def BIO[F[+_, +_]: BIOMonad, E, A](self: F[E, A]): BIOSyntax.BIOMonadOps[F, E, A] = new BIOSyntax.BIOMonadOps[F, E, A](self)
    @inline implicit final def BIO[F[+_, +_]: BIOGuarantee, E, A](self: F[E, A]): BIOSyntax.BIOGuaranteeOps[F, E, A] = new BIOSyntax.BIOGuaranteeOps[F, E, A](self)
    @inline implicit final def BIO[F[+_, +_]: BIOError, E, A](self: F[E, A]): BIOSyntax.BIOErrorOps[F, E, A] = new BIOSyntax.BIOErrorOps[F, E, A](self)
    @inline implicit final def BIO[F[+_, +_]: BIOMonadError, E, A](self: F[E, A]): BIOSyntax.BIOMonadErrorOps[F, E, A] = new BIOSyntax.BIOMonadErrorOps[F, E, A](self)
    @inline implicit final def BIO[F[+_, +_]: BIOBracket, E, A](self: F[E, A]): BIOSyntax.BIOBracketOps[F, E, A] = new BIOSyntax.BIOBracketOps[F, E, A](self)
    @inline implicit final def BIO[F[+_, +_]: BIOPanic, E, A](self: F[E, A]): BIOSyntax.BIOPanicOps[F, E, A] = new BIOSyntax.BIOPanicOps[F, E, A](self)
    @inline implicit final def BIO[F[+_, +_]: BIO, E, A](self: F[E, A]): BIOSyntax.BIOOps[F, E, A] = new BIOSyntax.BIOOps[F, E, A](self)
    @inline implicit final def BIO[F[+_, +_]: BIOMonad, E, A](self: F[E, F[E, A]])(implicit dummy: DummyImplicit): BIOSyntax.BIOFlattenOps[F, E, A] = new BIOSyntax.BIOFlattenOps[F, E, A](self)
  }
  trait BIOImplicitPuns2 extends BIOImplicitPuns3 {
    @inline implicit final def BIOPanic[F[_, +_]: BIOFunctor, E, A](self: F[E, A]): BIOSyntax.BIOFunctorOps[F, E, A] = new BIOSyntax.BIOFunctorOps[F, E, A](self)
    @inline implicit final def BIOPanic[F[+_, +_]: BIOBifunctor, E, A](self: F[E, A]): BIOSyntax.BIOBifunctorOps[F, E, A] = new BIOSyntax.BIOBifunctorOps[F, E, A](self)
    @inline implicit final def BIOPanic[F[+_, +_]: BIOApplicative, E, A](self: F[E, A]): BIOSyntax.BIOApplicativeOps[F, E, A] = new BIOSyntax.BIOApplicativeOps[F, E, A](self)
    @inline implicit final def BIOPanic[F[+_, +_]: BIOMonad, E, A](self: F[E, A]): BIOSyntax.BIOMonadOps[F, E, A] = new BIOSyntax.BIOMonadOps[F, E, A](self)
    @inline implicit final def BIOPanic[F[+_, +_]: BIOGuarantee, E, A](self: F[E, A]): BIOSyntax.BIOGuaranteeOps[F, E, A] = new BIOSyntax.BIOGuaranteeOps[F, E, A](self)
    @inline implicit final def BIOPanic[F[+_, +_]: BIOError, E, A](self: F[E, A]): BIOSyntax.BIOErrorOps[F, E, A] = new BIOSyntax.BIOErrorOps[F, E, A](self)
    @inline implicit final def BIOPanic[F[+_, +_]: BIOMonadError, E, A](self: F[E, A]): BIOSyntax.BIOMonadErrorOps[F, E, A] = new BIOSyntax.BIOMonadErrorOps[F, E, A](self)
    @inline implicit final def BIOPanic[F[+_, +_]: BIOBracket, E, A](self: F[E, A]): BIOSyntax.BIOBracketOps[F, E, A] = new BIOSyntax.BIOBracketOps[F, E, A](self)
    @inline implicit final def BIOPanic[F[+_, +_]: BIOPanic, E, A](self: F[E, A]): BIOSyntax.BIOPanicOps[F, E, A] = new BIOSyntax.BIOPanicOps[F, E, A](self)
    @inline implicit final def BIOPanic[F[+_, +_]: BIOMonad, E, A](self: F[E, F[E, A]])(implicit dummy: DummyImplicit): BIOSyntax.BIOFlattenOps[F, E, A] = new BIOSyntax.BIOFlattenOps[F, E, A](self)

    @inline final def BIOPanic[F[+_, +_]: BIOPanic]: BIOPanic[F] = implicitly
  }
  trait BIOImplicitPuns3 extends BIOImplicitPuns4 {
    @inline implicit final def BIOBracket[F[_, +_]: BIOFunctor, E, A](self: F[E, A]): BIOSyntax.BIOFunctorOps[F, E, A] = new BIOSyntax.BIOFunctorOps[F, E, A](self)
    @inline implicit final def BIOBracket[F[+_, +_]: BIOBifunctor, E, A](self: F[E, A]): BIOSyntax.BIOBifunctorOps[F, E, A] = new BIOSyntax.BIOBifunctorOps[F, E, A](self)
    @inline implicit final def BIOBracket[F[+_, +_]: BIOApplicative, E, A](self: F[E, A]): BIOSyntax.BIOApplicativeOps[F, E, A] = new BIOSyntax.BIOApplicativeOps[F, E, A](self)
    @inline implicit final def BIOBracket[F[+_, +_]: BIOMonad, E, A](self: F[E, A]): BIOSyntax.BIOMonadOps[F, E, A] = new BIOSyntax.BIOMonadOps[F, E, A](self)
    @inline implicit final def BIOBracket[F[+_, +_]: BIOGuarantee, E, A](self: F[E, A]): BIOSyntax.BIOGuaranteeOps[F, E, A] = new BIOSyntax.BIOGuaranteeOps[F, E, A](self)
    @inline implicit final def BIOBracket[F[+_, +_]: BIOError, E, A](self: F[E, A]): BIOSyntax.BIOErrorOps[F, E, A] = new BIOSyntax.BIOErrorOps[F, E, A](self)
    @inline implicit final def BIOBracket[F[+_, +_]: BIOMonadError, E, A](self: F[E, A]): BIOSyntax.BIOMonadErrorOps[F, E, A] = new BIOSyntax.BIOMonadErrorOps[F, E, A](self)
    @inline implicit final def BIOBracket[F[+_, +_]: BIOBracket, E, A](self: F[E, A]): BIOSyntax.BIOBracketOps[F, E, A] = new BIOSyntax.BIOBracketOps[F, E, A](self)
    @inline implicit final def BIOBracket[F[+_, +_]: BIOMonad, E, A](self: F[E, F[E, A]])(implicit dummy: DummyImplicit): BIOSyntax.BIOFlattenOps[F, E, A] = new BIOSyntax.BIOFlattenOps[F, E, A](self)

    @inline final def BIOBracket[F[+_, +_]: BIOBracket]: BIOBracket[F] = implicitly
  }
  trait BIOImplicitPuns4 extends BIOImplicitPuns5 {
    @inline implicit final def BIOMonadError[F[_, +_]: BIOFunctor, E, A](self: F[E, A]): BIOSyntax.BIOFunctorOps[F, E, A] = new BIOSyntax.BIOFunctorOps[F, E, A](self)
    @inline implicit final def BIOMonadError[F[+_, +_]: BIOBifunctor, E, A](self: F[E, A]): BIOSyntax.BIOBifunctorOps[F, E, A] = new BIOSyntax.BIOBifunctorOps[F, E, A](self)
    @inline implicit final def BIOMonadError[F[+_, +_]: BIOApplicative, E, A](self: F[E, A]): BIOSyntax.BIOApplicativeOps[F, E, A] = new BIOSyntax.BIOApplicativeOps[F, E, A](self)
    @inline implicit final def BIOMonadError[F[+_, +_]: BIOMonad, E, A](self: F[E, A]): BIOSyntax.BIOMonadOps[F, E, A] = new BIOSyntax.BIOMonadOps[F, E, A](self)
    @inline implicit final def BIOMonadError[F[+_, +_]: BIOGuarantee, E, A](self: F[E, A]): BIOSyntax.BIOGuaranteeOps[F, E, A] = new BIOSyntax.BIOGuaranteeOps[F, E, A](self)
    @inline implicit final def BIOMonadError[F[+_, +_]: BIOError, E, A](self: F[E, A]): BIOSyntax.BIOErrorOps[F, E, A] = new BIOSyntax.BIOErrorOps[F, E, A](self)
    @inline implicit final def BIOMonadError[F[+_, +_]: BIOMonadError, E, A](self: F[E, A]): BIOSyntax.BIOMonadErrorOps[F, E, A] = new BIOSyntax.BIOMonadErrorOps[F, E, A](self)
    @inline implicit final def BIOMonadError[F[+_, +_]: BIOMonad, E, A](self: F[E, F[E, A]])(implicit dummy: DummyImplicit): BIOSyntax.BIOFlattenOps[F, E, A] = new BIOSyntax.BIOFlattenOps[F, E, A](self)

    @inline final def BIOMonadError[F[+_, +_]: BIOMonadError]: BIOMonadError[F] = implicitly
  }
  trait BIOImplicitPuns5 extends BIOImplicitPuns6 {
    @inline implicit final def BIOError[F[_, +_]: BIOFunctor, E, A](self: F[E, A]): BIOSyntax.BIOFunctorOps[F, E, A] = new BIOSyntax.BIOFunctorOps[F, E, A](self)
    @inline implicit final def BIOError[F[+_, +_]: BIOBifunctor, E, A](self: F[E, A]): BIOSyntax.BIOBifunctorOps[F, E, A] = new BIOSyntax.BIOBifunctorOps[F, E, A](self)
    @inline implicit final def BIOError[F[+_, +_]: BIOApplicative, E, A](self: F[E, A]): BIOSyntax.BIOApplicativeOps[F, E, A] = new BIOSyntax.BIOApplicativeOps[F, E, A](self)
    @inline implicit final def BIOError[F[+_, +_]: BIOMonad, E, A](self: F[E, A]): BIOSyntax.BIOMonadOps[F, E, A] = new BIOSyntax.BIOMonadOps[F, E, A](self)
    @inline implicit final def BIOError[F[+_, +_]: BIOGuarantee, E, A](self: F[E, A]): BIOSyntax.BIOGuaranteeOps[F, E, A] = new BIOSyntax.BIOGuaranteeOps[F, E, A](self)
    @inline implicit final def BIOError[F[+_, +_]: BIOError, E, A](self: F[E, A]): BIOSyntax.BIOErrorOps[F, E, A] = new BIOSyntax.BIOErrorOps[F, E, A](self)

    @inline final def BIOError[F[+_, +_]: BIOError]: BIOError[F] = implicitly
  }
  trait BIOImplicitPuns6 extends BIOImplicitPuns7 {
    @inline implicit final def BIOGuarantee[F[_, +_]: BIOFunctor, E, A](self: F[E, A]): BIOSyntax.BIOFunctorOps[F, E, A] = new BIOSyntax.BIOFunctorOps[F, E, A](self)
    @inline implicit final def BIOGuarantee[F[+_, +_]: BIOBifunctor, E, A](self: F[E, A]): BIOSyntax.BIOBifunctorOps[F, E, A] = new BIOSyntax.BIOBifunctorOps[F, E, A](self)
    @inline implicit final def BIOGuarantee[F[+_, +_]: BIOApplicative, E, A](self: F[E, A]): BIOSyntax.BIOApplicativeOps[F, E, A] = new BIOSyntax.BIOApplicativeOps[F, E, A](self)
    @inline implicit final def BIOGuarantee[F[+_, +_]: BIOGuarantee, E, A](self: F[E, A]): BIOSyntax.BIOGuaranteeOps[F, E, A] = new BIOSyntax.BIOGuaranteeOps[F, E, A](self)

    @inline final def BIOGuarantee[F[+_, +_]: BIOGuarantee]: BIOGuarantee[F] = implicitly
  }
  trait BIOImplicitPuns7 extends BIOImplicitPuns8 {
    @inline implicit final def BIOMonad[F[_, +_]: BIOFunctor, E, A](self: F[E, A]): BIOSyntax.BIOFunctorOps[F, E, A] = new BIOSyntax.BIOFunctorOps[F, E, A](self)
    @inline implicit final def BIOMonad[F[+_, +_]: BIOBifunctor, E, A](self: F[E, A]): BIOSyntax.BIOBifunctorOps[F, E, A] = new BIOSyntax.BIOBifunctorOps[F, E, A](self)
    @inline implicit final def BIOMonad[F[+_, +_]: BIOApplicative, E, A](self: F[E, A]): BIOSyntax.BIOApplicativeOps[F, E, A] = new BIOSyntax.BIOApplicativeOps[F, E, A](self)
    @inline implicit final def BIOMonad[F[+_, +_]: BIOMonad, E, A](self: F[E, A]): BIOSyntax.BIOMonadOps[F, E, A] = new BIOSyntax.BIOMonadOps[F, E, A](self)
    @inline implicit final def BIOMonad[F[+_, +_]: BIOMonad, E, A](self: F[E, F[E, A]])(implicit dummy: DummyImplicit): BIOSyntax.BIOFlattenOps[F, E, A] = new BIOSyntax.BIOFlattenOps[F, E, A](self)

    @inline final def BIOMonad[F[+_, +_]: BIOMonad]: BIOMonad[F] = implicitly
  }
  trait BIOImplicitPuns8 extends BIOImplicitPuns9 {
    @inline implicit final def BIOApplicative[F[_, +_]: BIOFunctor, E, A](self: F[E, A]): BIOSyntax.BIOFunctorOps[F, E, A] = new BIOSyntax.BIOFunctorOps[F, E, A](self)
    @inline implicit final def BIOApplicative[F[+_, +_]: BIOBifunctor, E, A](self: F[E, A]): BIOSyntax.BIOBifunctorOps[F, E, A] = new BIOSyntax.BIOBifunctorOps[F, E, A](self)
    @inline implicit final def BIOApplicative[F[+_, +_]: BIOApplicative, E, A](self: F[E, A]): BIOSyntax.BIOApplicativeOps[F, E, A] = new BIOSyntax.BIOApplicativeOps[F, E, A](self)

    @inline final def BIOApplicative[F[+_, +_]: BIOApplicative]: BIOApplicative[F] = implicitly
  }
  trait BIOImplicitPuns9 extends BIOImplicitPuns10 {
    @inline implicit final def BIOBifunctor[F[_, +_]: BIOFunctor, E, A](self: F[E, A]): BIOSyntax.BIOFunctorOps[F, E, A] = new BIOSyntax.BIOFunctorOps[F, E, A](self)
    @inline implicit final def BIOBifunctor[F[+_, +_]: BIOBifunctor, E, A](self: F[E, A]): BIOSyntax.BIOBifunctorOps[F, E, A] = new BIOSyntax.BIOBifunctorOps[F, E, A](self)

    @inline final def BIOBifunctor[F[+_, +_]: BIOBifunctor]: BIOBifunctor[F] = implicitly
  }
  trait BIOImplicitPuns10 {
    @inline implicit final def BIOFunctor[F[_, + _] : BIOFunctor, E, A](self: F[E, A]): BIOSyntax.BIOFunctorOps[F, E, A] = new BIOSyntax.BIOFunctorOps[F, E, A](self)

    @inline final def BIOFunctor[F[_, +_]: BIOFunctor]: BIOFunctor[F] = implicitly
  }
}
