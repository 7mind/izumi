package izumi.functional

import izumi.functional.bio.syntax.{BIO3Syntax, BIOSyntax}
import izumi.functional.mono.{Clock, Entropy, SyncSafe}

/**
  *  Current hierarchy (use http://www.nomnoml.com/ to render, rendered: https://izumi.7mind.io/bio/media/bio-relationship-hierarchy.svg)
  *
  *  {{{
  *  [BIOFunctor3]<--[BIOBifunctor3]
  *  [BIOBifunctor3]<--[BIOApplicativeError3]
  *  [BIOFunctor3]<--[BIOApplicative3]
  *  [BIOApplicative3]<--[BIOGuarantee3]
  *  [BIOApplicative3]<--[BIOMonad3]
  *  [BIOGuarantee3]<--[BIOApplicativeError3]
  *  [BIOApplicativeError3]<--[BIOError3]
  *  [BIOMonad3]<--[BIOError3]
  *  [BIOError3]<--[BIOBracket3]
  *  [BIOBracket3]<--[BIOPanic3]
  *  [BIOPanic3]<--[BIO3]
  *  [BIO3]<--[BIOAsync3]
  *
  *  [BIOMonad3]<--[BIOParallel3]
  *  [BIOParallel3]<--[BIOConcurrent3]
  *  [BIOConcurrent3]<--[BIOAsync3]
  *
  *  [BIOError3]<--[BIOTemporal3]
  *
  *  [BIOFunctor3]<--[BIOProfunctor]
  *  [BIOProfunctor]<--[BIOArrow]
  *  [BIOArrow]<--[BIOArrowChoice]
  *  [BIOArrowChoice]<--[BIOLocal]
  *
  *  [BIOApplicative3]<--[BIOAsk]
  *  [BIOMonad3]<--[BIOMonadAsk]
  *  [BIOAsk]<--[BIOMonadAsk]
  *  [BIOMonadAsk]<--[BIOLocal]
  *  }}}
  *
  *  Auxiliary algebras:
  *
  *  {{{
  *  [cats.effect.*]<:--[BIOCatsConversions]
  *
  *  [BIOFiber]<:--[BIOFork3]
  *  [BIOFork3]<:--[BIOFork]
  *
  *  [BlockingIO3]<:--[BlockingIO]
  *
  *  [BIOPromise]<:--[BIOPrimitives3]
  *  [BIOSemaphore]<:--[BIOPrimitives3]
  *  [BIORef]<:--[BIOPrimitives3]
  *  [BIOPrimitives3]<:--[BIOPrimitives]
  *
  *  [Entropy3]<:--[Entropy2]
  *  [Entropy2]<:--[Entropy]
  *
  *  [Clock3]<:--[Clock2]
  *  [Clock2]<:--[Clock]
  *
  *  [BIORunner]
  *  }}}
  *
  *  inheritance hierarchy:
  *
  *  {{{
  *  [BIOFunctor3]<--[BIOApplicative3]
  *  [BIOApplicative3]<--[BIOGuarantee3]
  *  [BIOApplicative3]<--[BIOMonad3]
  *  [BIOGuarantee3]<--[BIOApplicativeError3]
  *  [BIOBifunctor3]<--[BIOApplicativeError3]
  *  [BIOApplicativeError3]<--[BIOError3]
  *  [BIOMonad3]<--[BIOError3]
  *  [BIOError3]<--[BIOBracket3]
  *  [BIOBracket3]<--[BIOPanic3]
  *  [BIOPanic3]<--[BIO3]
  *
  *  [BIOParallel3]<--[BIOConcurrent3]
  *  [BIOConcurrent3]<--[BIOAsync3]
  *  [BIO3]<--[BIOAsync3]
  *
  *  [BIOTemporal3]
  *
  *  [BIOProfunctor]<--[BIOArrow]
  *  [BIOArrow]<--[BIOArrowChoice]
  *  [BIOArrowChoice]<--[BIOLocal]
  *  [BIOAsk]<--[BIOMonadAsk]
  *  [BIOMonadAsk]<--[BIOLocal]
  *  }}}
  *
  *  current hierarchy roots:
  *
  *  bifunctor:
  *  - BIOFunctor3
  *  - BIOBifunctor3
  *  - BIOParallel3
  *  - BIOTemporal3
  *
  *  trifunctor:
  *  - BIOProfunctor
  *  - BIOAsk
  *
  *  standalone:
  *  - BIOFork3
  *  - BlockingIO3
  *  - BIOPrimitives
  */
/*
  New BIO typeclass checklist:

  [ ] - add syntax in BIOSyntax3 & BIOSyntax at the same name as type
  [ ] - add syntax for new root's InnerF at the same name in BIOSyntax3 & BIOSyntax
  [ ] - add new attachments in BIORootInstanceLowPriorityN
  [ ] - add conversion BIOConvertToBIONewRoot in BIORootInstanceLowPriorityN
        (conversions implicit priority: from most specific InnerF to least specific)
 */
package object bio extends BIO3Syntax with BIOSyntax with DeprecatedAliases {

  /**
    * A convenient dependent summoner for BIO* hierarchy.
    * Auto-narrows to the most powerful available class:
    *
    * {{{
    *   def y[F[+_, +_]: BIOTemporal] = {
    *     F.timeout(5.seconds)(F.forever(F.unit))
    *   }
    * }}}
    */
  @inline override final def F[FR[-_, +_, +_]](implicit FR: Functor3[FR]): FR.type = FR

  type Functor2[F[+_, +_]] = Functor3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  type Bifunctor2[F[+_, +_]] = Bifunctor3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  type Applicative2[F[+_, +_]] = Applicative3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  type Guarantee2[F[+_, +_]] = Guarantee3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  type ApplicativeError2[F[+_, +_]] = ApplicativeError3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  type Monad2[F[+_, +_]] = Monad3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  type Error2[F[+_, +_]] = Error3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  type Bracket2[F[+_, +_]] = Bracket3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  type Panic2[F[+_, +_]] = Panic3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  type IO2[F[+_, +_]] = IO3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  type Parallel2[F[+_, +_]] = Parallel3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  type Concurrent2[F[+_, +_]] = Concurrent3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  type Async2[F[+_, +_]] = Async3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  type Temporal2[F[+_, +_]] = Temporal3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]

  type Fork2[F[+_, +_]] = Fork3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]

  type Fiber2[+F[+_, +_], +E, +A] = Fiber3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]], E, A]
  lazy val Fiber2 = Fiber3

  type Ref3[+F[-_, +_, +_], A] = Ref2[F[Any, +?, +?], A]

  type Promise3[+F[-_, +_, +_], E, A] = Promise2[F[Any, +?, +?], E, A]

  type Latch2[+F[+_, +_]] = Promise2[F, Nothing, Unit]
  type Latch3[+F[-_, +_, +_]] = Promise3[F, Nothing, Unit]

  type Semaphore3[+F[-_, +_, +_]] = Semaphore2[F[Any, +?, +?]]

  type Primitives3[F[-_, +_, +_]] = Primitives2[F[Any, +?, +?]]
  object Primitives3 {
    @inline def apply[F[-_, +_, +_]: Primitives3]: Primitives3[F] = implicitly
  }

  type UnsafeRun3[F[_, _, _]] = UnsafeRun2[F[Any, ?, ?]]
  object UnsafeRun3 {
    @inline def apply[F[_, _, _]: UnsafeRun3]: UnsafeRun3[F] = implicitly
  }

  type BlockingIO[F[+_, +_]] = BlockingIO3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  object BlockingIO {
    @inline def apply[F[+_, +_]: BlockingIO]: BlockingIO[F] = implicitly
  }

  type SyncSafe2[F[_, _]] = SyncSafe[F[Nothing, ?]]
  object SyncSafe2 {
    @inline def apply[F[_, _]: SyncSafe2]: SyncSafe2[F] = implicitly
  }
  type SyncSafe3[F[_, _, _]] = SyncSafe[F[Any, Nothing, ?]]
  object SyncSafe3 {
    @inline def apply[F[_, _, _]: SyncSafe3]: SyncSafe3[F] = implicitly
  }

  type Clock2[F[_, _]] = Clock[F[Nothing, ?]]
  object Clock2 {
    @inline def apply[F[_, _]: Clock2]: Clock2[F] = implicitly
  }
  type Clock3[F[_, _, _]] = Clock[F[Any, Nothing, ?]]
  object Clock3 {
    @inline def apply[F[_, _, _]: Clock3]: Clock3[F] = implicitly
  }

  type Entropy2[F[_, _]] = Entropy[F[Nothing, ?]]
  object Entropy2 {
    @inline def apply[F[_, _]: Entropy2]: Entropy2[F] = implicitly
  }
  type Entropy3[F[_, _, _]] = Entropy[F[Any, Nothing, ?]]
  object Entropy3 {
    @inline def apply[F[_, _, _]: Entropy3]: Entropy3[F] = implicitly
  }

  @inline private[bio] final def cast3To2[C[_[-_, +_, +_]], FR[-_, +_, +_], R](
    instance: C[FR]
  ): C[Lambda[(`-R0`, `+E`, `+A`) => FR[R, E, A]]] = {
    instance.asInstanceOf[C[Lambda[(`-R0`, `+E`, `+A`) => FR[R, E, A]]]]
  }

}
