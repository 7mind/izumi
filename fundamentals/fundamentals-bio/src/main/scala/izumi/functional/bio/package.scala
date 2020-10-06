package izumi.functional

import izumi.functional.bio.syntax.{Syntax2, Syntax3}
import izumi.functional.mono.{Clock, Entropy, SyncSafe}

/**
  *  Current hierarchy (use http://www.nomnoml.com/ to render, rendered: https://izumi.7mind.io/bio/media/bio-relationship-hierarchy.svg)
  *
  *  {{{
  *  [Functor3]<--[Bifunctor3]
  *  [Bifunctor3]<--[ApplicativeError3]
  *  [Functor3]<--[Applicative3]
  *  [Applicative3]<--[Guarantee3]
  *  [Applicative3]<--[Monad3]
  *  [Guarantee3]<--[ApplicativeError3]
  *  [ApplicativeError3]<--[Error3]
  *  [Monad3]<--[Error3]
  *  [Error3]<--[Bracket3]
  *  [Bracket3]<--[Panic3]
  *  [Panic3]<--[IO3]
  *  [IO3]<--[Async3]
  *
  *  [Monad3]<--[Parallel3]
  *  [Parallel3]<--[Concurrent3]
  *  [Concurrent3]<--[Async3]
  *
  *  [Error3]<--[Temporal3]
  *
  *  [Functor3]<--[Profunctor3]
  *  [Profunctor3]<--[Arrow3]
  *  [Arrow3]<--[ArrowChoice3]
  *  [ArrowChoice3]<--[Local3]
  *
  *  [Applicative3]<--[Ask3]
  *  [Monad3]<--[MonadAsk3]
  *  [Ask3]<--[MonadAsk3]
  *  [MonadAsk3]<--[Local3]
  *  }}}
  *
  *  Auxiliary algebras:
  *
  *  {{{
  *  [cats.effect.*]<:--[CatsConversions]
  *
  *  [Fiber2]<:--[Fork3]
  *  [Fork3]<:--[Fork2]
  *
  *  [BlockingIO3]<:--[BlockingIO]
  *
  *  [Promise2]<:--[Primitives3]
  *  [Semaphore2]<:--[Primitives3]
  *  [Ref2]<:--[Primitives3]
  *  [Primitives3]<:--[Primitives2]
  *
  *  [Entropy3]<:--[Entropy2]
  *  [Entropy2]<:--[Entropy]
  *
  *  [Clock3]<:--[Clock2]
  *  [Clock2]<:--[Clock]
  *
  *  [UnsafeRun2]
  *  }}}
  *
  *  inheritance hierarchy:
  *
  *  {{{
  *  [Functor3]<--[Applicative3]
  *  [Applicative3]<--[Guarantee3]
  *  [Applicative3]<--[Monad3]
  *  [Guarantee3]<--[ApplicativeError3]
  *  [Bifunctor3]<--[ApplicativeError3]
  *  [ApplicativeError3]<--[Error3]
  *  [Monad3]<--[Error3]
  *  [Error3]<--[Bracket3]
  *  [Bracket3]<--[Panic3]
  *  [Panic3]<--[IO3]
  *
  *  [Parallel3]<--[Concurrent3]
  *  [Concurrent3]<--[Async3]
  *  [IO3]<--[Async3]
  *
  *  [Temporal3]
  *
  *  [Profunctor3]<--[Arrow3]
  *  [Arrow3]<--[ArrowChoice3]
  *  [ArrowChoice3]<--[Local3]
  *  [Ask3]<--[MonadAsk3]
  *  [MonadAsk3]<--[Local3]
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
  *  trifunctor:
  *  - Profunctor3
  *  - Ask3
  *
  *  standalone:
  *  - Fork3
  *  - BlockingIO3
  *  - Primitives3
  */
/*
  New BIO typeclass checklist:

  [ ] - add syntax in BIOSyntax3 & BIOSyntax at the same name as type
  [ ] - add syntax for new root's InnerF at the same name in BIOSyntax3 & BIOSyntax
  [ ] - add new attachments in BIORootInstanceLowPriorityN
  [ ] - add conversion BIOConvertToBIONewRoot in BIORootInstanceLowPriorityN
        (conversions implicit priority: from most specific InnerF to least specific)
 */
package object bio extends Syntax3 with Syntax2 {

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
  lazy val Fiber2: Fiber3.type = Fiber3

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

  // Deprecated aliases

  @deprecated("renamed to Functor2", "0.11")
  type BIOFunctor[F[+_, +_]] = Functor3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  @deprecated("renamed to Bifunctor2", "0.11")
  type BIOBifunctor[F[+_, +_]] = Bifunctor3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  @deprecated("renamed to Applicative2", "0.11")
  type BIOApplicative[F[+_, +_]] = Applicative3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  @deprecated("renamed to Guarantee2", "0.11")
  type BIOGuarantee[F[+_, +_]] = Guarantee3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  @deprecated("renamed to ApplicativeError2", "0.11")
  type BIOApplicativeError[F[+_, +_]] = ApplicativeError3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  @deprecated("renamed to Monad2", "0.11")
  type BIOMonad[F[+_, +_]] = Monad3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  @deprecated("renamed to Error2", "0.11")
  type BIOError[F[+_, +_]] = Error3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  @deprecated("renamed to Bracket2", "0.11")
  type BIOBracket[F[+_, +_]] = Bracket3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  @deprecated("renamed to Panic2", "0.11")
  type BIOPanic[F[+_, +_]] = Panic3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  @deprecated("renamed to IO2", "0.11")
  type BIO[F[+_, +_]] = IO3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  @deprecated("renamed to Parallel2", "0.11")
  type BIOParallel[F[+_, +_]] = Parallel3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  @deprecated("renamed to Concurrent2", "0.11")
  type BIOConcurrent[F[+_, +_]] = Concurrent3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  @deprecated("renamed to Async2", "0.11")
  type BIOAsync[F[+_, +_]] = Async3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  @deprecated("renamed to Temporal2", "0.11")
  type BIOTemporal[F[+_, +_]] = Temporal3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]

  @deprecated("renamed to Functor3", "0.11")
  type BIOFunctor3[F[-_, +_, +_]] = Functor3[F]
  @deprecated("renamed to Bifunctor3", "0.11")
  type BIOBifunctor3[F[-_, +_, +_]] = Bifunctor3[F]
  @deprecated("renamed to Applicative3", "0.11")
  type BIOApplicative3[F[-_, +_, +_]] = Applicative3[F]
  @deprecated("renamed to Guarantee3", "0.11")
  type BIOGuarantee3[F[-_, +_, +_]] = Guarantee3[F]
  @deprecated("renamed to ApplicativeError3", "0.11")
  type BIOApplicativeError3[F[-_, +_, +_]] = ApplicativeError3[F]
  @deprecated("renamed to Monad3", "0.11")
  type BIOMonad3[F[-_, +_, +_]] = Monad3[F]
  @deprecated("renamed to Error3", "0.11")
  type BIOError3[F[-_, +_, +_]] = Error3[F]
  @deprecated("renamed to Bracket3", "0.11")
  type BIOBracket3[F[-_, +_, +_]] = Bracket3[F]
  @deprecated("renamed to Panic3", "0.11")
  type BIOPanic3[F[-_, +_, +_]] = Panic3[F]
  @deprecated("renamed to IO3", "0.11")
  type BIO3[F[-_, +_, +_]] = IO3[F]
  @deprecated("renamed to Parallel3", "0.11")
  type BIOParallel3[F[-_, +_, +_]] = Parallel3[F]
  @deprecated("renamed to Concurrent3", "0.11")
  type BIOConcurrent3[F[-_, +_, +_]] = Concurrent3[F]
  @deprecated("renamed to Async3", "0.11")
  type BIOAsync3[F[-_, +_, +_]] = Async3[F]
  @deprecated("renamed to Temporal3", "0.11")
  type BIOTemporal3[F[-_, +_, +_]] = Temporal3[F]

  @deprecated("renamed to Ask3", "0.11")
  type BIOAsk[F[-_, +_, +_]] = Ask3[F]
  @deprecated("renamed to MonadAsk3", "0.11")
  type BIOMonadAsk[F[-_, +_, +_]] = MonadAsk3[F]
  @deprecated("renamed to Profunctor3", "0.11")
  type BIOProfunctor[F[-_, +_, +_]] = Profunctor3[F]
  @deprecated("renamed to Arrow3", "0.11")
  type BIOArrow[F[-_, +_, +_]] = Arrow3[F]
  @deprecated("renamed to ArrowChoice3", "0.11")
  type BIOArrowChoice[F[-_, +_, +_]] = ArrowChoice3[F]
  @deprecated("renamed to Local3", "0.11")
  type BIOLocal[F[-_, +_, +_]] = Local3[F]

  @deprecated("renamed to Fork2", "0.11")
  type BIOFork[F[+_, +_]] = Fork3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  @deprecated("renamed to Fork3", "0.11")
  type BIOFork3[F[-_, +_, +_]] = Fork3[F]

  @deprecated("renamed to Fiber2", "0.11")
  type BIOFiber[F[+_, +_], +E, +A] = Fiber3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]], E, A]
  @deprecated("renamed to Fiber3", "0.11")
  type BIOFiber3[F[-_, +_, +_], +E, +A] = Fiber3[F, E, A]
  @deprecated("renamed to Fiber3", "0.11")
  lazy val BIOFiber3: Fiber3.type = Fiber3

  @deprecated("renamed to Ref2", "0.11")
  type BIORef[F[+_, +_], A] = Ref2[F, A]
  @deprecated("renamed to Ref2", "0.11")
  lazy val BIORef: Ref2.type = Ref2
  @deprecated("renamed to Ref3", "0.11")
  type BIORef3[F[-_, +_, +_], A] = Ref3[F, A]

  @deprecated("renamed to Promise2", "0.11")
  type BIOPromise[F[+_, +_], E, A] = Promise2[F, E, A]
  @deprecated("renamed to Promise2", "0.11")
  lazy val BIOPromise: Promise2.type = Promise2
  @deprecated("renamed to Promise3", "0.11")
  type BIOPromise3[F[-_, +_, +_], E, A] = Promise3[F, E, A]

  @deprecated("renamed to Latch2", "0.11")
  type BIOLatch[F[+_, +_]] = Latch2[F]
  @deprecated("renamed to Latch3", "0.11")
  type BIOLatch3[F[-_, +_, +_]] = Latch3[F]

  @deprecated("renamed to Semaphore2", "0.11")
  type BIOSemaphore[F[+_, +_]] = Semaphore2[F]
  @deprecated("renamed to Semaphore2", "0.11")
  lazy val BIOSemaphore: Semaphore2.type = Semaphore2
  @deprecated("renamed to Semaphore3", "0.11")
  type BIOSemaphore3[F[-_, +_, +_]] = Semaphore3[F]

  @deprecated("renamed to Primitives2", "0.11")
  type BIOPrimitives[F[+_, +_]] = Primitives2[F]
  @deprecated("renamed to Primitives2", "0.11")
  lazy val BIOPrimitives: Primitives2.type = Primitives2
  @deprecated("renamed to Primitives3", "0.11")
  type BIOPrimitives3[F[-_, +_, +_]] = Primitives2[F[Any, +?, +?]]
  @deprecated("renamed to Primitives3", "0.11")
  lazy val BIOPrimitives3: Primitives3.type = Primitives3

  @deprecated("renamed to UnsafeRun2", "0.11")
  type BIORunner[F[_, _]] = UnsafeRun2[F]
  @deprecated("renamed to UnsafeRun2", "0.11")
  lazy val BIORunner: UnsafeRun2.type = UnsafeRun2

  @deprecated("renamed to UnsafeRun3", "0.11")
  type BIORunner3[F[_, _, _]] = UnsafeRun3[F]
  @deprecated("renamed to UnsafeRun3", "0.11")
  lazy val BIORunner3: UnsafeRun3.type = UnsafeRun3

  @deprecated("renamed to Error2", "0.11")
  type BIOMonadError[F[+_, +_]] = Error3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]

  @deprecated("renamed to Error3", "0.11")
  type BIOMonadError3[FR[-_, +_, +_]] = Error3[FR]

  @deprecated("renamed to Exit", "0.11")
  type BIOExit[+E, +A] = Exit[E, A]
  @deprecated("renamed to Exit", "0.11")
  lazy val BIOExit: Exit.type = Exit

  @deprecated("renamed to TransZio", "0.11")
  type BIOTransZio[F[_, _]] = TransZio[F]
  @deprecated("renamed to TransZio", "0.11")
  lazy val BIOTransZio: TransZio.type = TransZio

  @deprecated("renamed to Root", "0.11")
  type BIORoot = Root
  @deprecated("renamed to Root", "0.11")
  lazy val BIORoot: Root.type = Root

}
