package izumi.functional

import izumi.functional.bio.PredefinedHelper.NotPredefined
import izumi.functional.bio.data.Isomorphism2
import izumi.functional.bio.syntax.Syntax2

import scala.annotation.unused
import scala.language.implicitConversions

/**
  *  Current hierarchy (use http://www.nomnoml.com/ to render, rendered: https://izumi.7mind.io/bio/media/bio-relationship-hierarchy.svg)
  *
  *  {{{
  *  [Functor2]<--[Bifunctor2]
  *  [Bifunctor2]<--[ApplicativeError2]
  *  [Functor2]<--[Applicative2]
  *  [Applicative2]<--[Guarantee2]
  *  [Applicative2]<--[Monad2]
  *  [Guarantee2]<--[ApplicativeError2]
  *  [ApplicativeError2]<--[Error2]
  *  [Monad2]<--[Error2]
  *  [Error2]<--[Bracket2]
  *  [Bracket2]<--[Panic2]
  *  [Panic2]<--[IO2]
  *  [IO2]<--[Async2]
  *
  *  [Monad2]<--[Parallel2]
  *  [Parallel2]<--[Concurrent2]
  *  [Concurrent2]<--[Async2]
  *
  *  [Error2]<--[Temporal2]
  *  }}}
  *
  *  Auxiliary algebras:
  *
  *  {{{
  *  [cats.effect.*]<:--[CatsConversions]
  *
  *  [Fork2]<:--[Fiber2]
  *
  *  [BlockingIO2]
  *
  *  [Primitives2]
  *
  *  [Primitives2]<:--[Ref2]
  *  [Primitives2]<:--[Semaphore2]
  *  [Primitives2]<:--[Promise2]
  *
  *  [PrimitivesM2]
  *  [PrimitivesM2]<:--[RefM2]
  *  [PrimitivesM2]<:--[Mutex2]
  *
  *  [Entropy1]<:--[Entropy2]
  *  [Clock1]<:--[Clock2]
  *
  *  [UnsafeRun2]
  *  }}}
  *
  *  Raw inheritance hierarchy:
  *
  *  {{{
  *  [Functor2]<--[Applicative2]
  *  [Applicative2]<--[Guarantee2]
  *  [Applicative2]<--[Monad2]
  *  [Guarantee2]<--[ApplicativeError2]
  *  [Bifunctor2]<--[ApplicativeError2]
  *  [ApplicativeError2]<--[Error2]
  *  [Monad2]<--[Error2]
  *  [Error2]<--[Bracket2]
  *  [Bracket2]<--[Panic2]
  *  [Panic2]<--[IO2]
  *
  *  [Parallel2]<--[Concurrent2]
  *  [Concurrent2]<--[Async2]
  *  [IO2]<--[Async2]
  *
  *  [Temporal2]
  *  }}}
  *
  *  current hierarchy roots:
  *
  *  bifunctor:
  *  - Functor3
  *  - Bifunctor3
  *  - Parallel3
  *  - Temporal3
  *
  *  standalone:
  *  - Fork3
  *  - BlockingIO3
  *  - Primitives3
  *  - PrimitivesM3
  */
/*
  New BIO typeclass checklist:

  [ ] - add syntax in BIOSyntax3 & BIOSyntax at the same name as type
  [ ] - add syntax for new root's InnerF at the same name in BIOSyntax3 & BIOSyntax
  [ ] - add new attachments in BIORootInstanceLowPriorityN
  [ ] - add conversion BIOConvertToBIONewRoot in BIORootInstanceLowPriorityN
        (conversions implicit priority: from most specific InnerF to least specific)
 */
package object bio extends Syntax2 {

  /**
    * A convenient dependent summoner for BIO hierarchy.
    * Auto-narrows to the most powerful available class:
    *
    * {{{
    *   import izumi.functional.bio.{F, Temporal2}
    *
    *   def y[F[+_, +_]: Temporal2] = {
    *     F.timeout(5.seconds)(F.unit)
    *   }
    * }}}
    */
  @inline override final def F[F[+_, +_]](implicit F: Functor2[F]): F.type = F

  @inline final def MoreCursedF[F[+_, +_]](implicit F: NotPredefined.Of[Functor2[F]] = CursedPower.cursedManifestation): F.type = F

  object CursedPower {
    type CursedPower <: Functor2[Nothing]
    val cursedManifestation: CursedPower = null.asInstanceOf[CursedPower]

    implicit final def CursedAttachPrimitives2[F[+_, +_]](@unused bearerOfTheCurse: CursedPower)(implicit Primitives: Primitives2[F]): Primitives.type =
      Primitives
  }

  type TransZio[F[_, _]] = Isomorphism2[F, zio.IO]
  object TransZio {
    @inline def apply[F[_, _]: TransZio]: TransZio[F] = implicitly
  }

  type Ref2[+F[_, _], A] = Ref1[F[Nothing, _], A]
  lazy val Ref2: Ref1.type = Ref1

  type Latch2[+F[+_, +_]] = Promise2[F, Nothing, Unit]
  lazy val Latch2: Promise2.type = Promise2

  type Semaphore2[+F[_, _]] = Semaphore1[F[Nothing, _]]
  lazy val Semaphore2: Semaphore1.type = Semaphore1

  type SyncSafe2[F[_, _]] = SyncSafe1[F[Nothing, _]]
  object SyncSafe2 {
    @inline def apply[F[_, _]: SyncSafe2]: SyncSafe2[F] = implicitly
  }
  type SyncSafe3[F[_, _, _]] = SyncSafe1[F[Any, Nothing, _]]
  object SyncSafe3 {
    @inline def apply[F[_, _, _]: SyncSafe3]: SyncSafe3[F] = implicitly
  }

  type Clock2[F[_, _]] = Clock1[F[Nothing, _]]
  object Clock2 {
    @inline def apply[F[_, _]: Clock2]: Clock2[F] = implicitly
  }
  type Clock3[F[_, _, _]] = Clock1[F[Any, Nothing, _]]
  object Clock3 {
    @inline def apply[F[_, _, _]: Clock3]: Clock3[F] = implicitly
  }

  type Entropy2[F[_, _]] = Entropy1[F[Nothing, _]]
  object Entropy2 {
    @inline def apply[F[_, _]: Entropy2]: Entropy2[F] = implicitly
  }
  type Entropy3[F[_, _, _]] = Entropy1[F[Any, Nothing, _]]
  object Entropy3 {
    @inline def apply[F[_, _, _]: Entropy3]: Entropy3[F] = implicitly
  }

}
