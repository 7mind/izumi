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
package object bio extends BIO3Syntax with BIOSyntax {

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
  @inline override final def F[FR[-_, +_, +_]](implicit FR: BIOFunctor3[FR]): FR.type = FR

  type BIOFunctor[F[+_, +_]] = BIOFunctor3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  type BIOBifunctor[F[+_, +_]] = BIOBifunctor3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  type BIOApplicative[F[+_, +_]] = BIOApplicative3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  type BIOGuarantee[F[+_, +_]] = BIOGuarantee3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  type BIOApplicativeError[F[+_, +_]] = BIOApplicativeError3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  type BIOMonad[F[+_, +_]] = BIOMonad3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  type BIOError[F[+_, +_]] = BIOError3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  type BIOBracket[F[+_, +_]] = BIOBracket3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  type BIOPanic[F[+_, +_]] = BIOPanic3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  type BIO[F[+_, +_]] = BIO3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  type BIOParallel[F[+_, +_]] = BIOParallel3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  type BIOConcurrent[F[+_, +_]] = BIOConcurrent3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  type BIOAsync[F[+_, +_]] = BIOAsync3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  type BIOTemporal[F[+_, +_]] = BIOTemporal3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]

  type BIOFork[F[+_, +_]] = BIOFork3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  type BIOFiber[F[+_, +_], +E, +A] = BIOFiber3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]], E, A]

  type BIORef3[F[-_, +_, +_], A] = BIORef[F[Any, +?, +?], A]
  type BIOPromise3[F[-_, +_, +_], E, A] = BIOPromise[F[Any, +?, +?], E, A]
  type BIOSemaphore3[F[-_, +_, +_]] = BIOSemaphore[F[Any, +?, +?]]
  type BIOLatch[F[+_, +_]] = BIOPromise[F, Nothing, Unit]
  type BIOLatch3[F[-_, +_, +_]] = BIOPromise3[F, Nothing, Unit]

  type BIOPrimitives3[F[-_, +_, +_]] = BIOPrimitives[F[Any, +?, +?]]
  object BIOPrimitives3 {
    def apply[F[-_, +_, +_]: BIOPrimitives3]: BIOPrimitives3[F] = implicitly
  }

  type BlockingIO[F[+_, +_]] = BlockingIO3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  object BlockingIO {
    def apply[F[+_, +_]: BlockingIO]: BlockingIO[F] = implicitly
  }

  type SyncSafe2[F[_, _]] = SyncSafe[F[Nothing, ?]]
  object SyncSafe2 {
    def apply[F[_, _]: SyncSafe2]: SyncSafe2[F] = implicitly
  }
  type SyncSafe3[F[_, _, _]] = SyncSafe[F[Any, Nothing, ?]]
  object SyncSafe3 {
    def apply[F[_, _, _]: SyncSafe3]: SyncSafe3[F] = implicitly
  }

  type Clock2[F[_, _]] = Clock[F[Nothing, ?]]
  object Clock2 {
    def apply[F[_, _]: Clock2]: Clock2[F] = implicitly
  }
  type Clock3[F[_, _, _]] = Clock[F[Any, Nothing, ?]]
  object Clock3 {
    def apply[F[_, _, _]: Clock3]: Clock3[F] = implicitly
  }

  type Entropy2[F[_, _]] = Entropy[F[Nothing, ?]]
  object Entropy2 {
    def apply[F[_, _]: Entropy2]: Entropy2[F] = implicitly
  }
  type Entropy3[F[_, _, _]] = Entropy[F[Any, Nothing, ?]]
  object Entropy3 {
    def apply[F[_, _, _]: Entropy3]: Entropy3[F] = implicitly
  }

  @inline private[bio] final def cast3To2[C[_[-_, +_, +_]], FR[-_, +_, +_], R](
    instance: C[FR]
  ): C[Lambda[(`-R0`, `+E`, `+A`) => FR[R, E, A]]] = {
    instance.asInstanceOf[C[Lambda[(`-R0`, `+E`, `+A`) => FR[R, E, A]]]]
  }

  @deprecated("Use BIOError", "0.11")
  type BIOMonadError[F[+_, +_]] = BIOError3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]

  @deprecated("Use BIOError3", "0.11")
  type BIOMonadError3[FR[-_, +_, +_]] = BIOError3[FR]
}
