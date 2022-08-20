package izumi.functional

import izumi.functional.bio.data.Isomorphism2
import izumi.functional.bio.syntax.{Syntax2, Syntax3}

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
  *  [Fork3]<:--[Fiber3]
  *
  *  [BlockingIO3]<:--[BlockingIO2]
  *
  *  [Primitives2]<:--[Primitives3]
  *  [Primitives3]<:--[Ref3]
  *
  *  [Primitives3]<:--[Semaphore3]
  *  [Primitives3]<:--[Promise3]
  *
  *  [Entropy]<:--[Entropy2]
  *
  *  [Entropy2]<:--[Entropy3]
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
  @inline override final def F[FR[-_, +_, +_]](implicit FR: Functor3[FR]): FR.type = FR

  type Functor2[F[+_, +_]] = Functor3[λ[(`-R`, `+E`, `+A`) => F[E, A]]]
  type Bifunctor2[F[+_, +_]] = Bifunctor3[λ[(`-R`, `+E`, `+A`) => F[E, A]]]
  type Applicative2[F[+_, +_]] = Applicative3[λ[(`-R`, `+E`, `+A`) => F[E, A]]]
  type Guarantee2[F[+_, +_]] = Guarantee3[λ[(`-R`, `+E`, `+A`) => F[E, A]]]
  type ApplicativeError2[F[+_, +_]] = ApplicativeError3[λ[(`-R`, `+E`, `+A`) => F[E, A]]]
  type Monad2[F[+_, +_]] = Monad3[λ[(`-R`, `+E`, `+A`) => F[E, A]]]
  type Error2[F[+_, +_]] = Error3[λ[(`-R`, `+E`, `+A`) => F[E, A]]]
  type Bracket2[F[+_, +_]] = Bracket3[λ[(`-R`, `+E`, `+A`) => F[E, A]]]
  type Panic2[F[+_, +_]] = Panic3[λ[(`-R`, `+E`, `+A`) => F[E, A]]]
  type IO2[F[+_, +_]] = IO3[λ[(`-R`, `+E`, `+A`) => F[E, A]]]
  type Parallel2[F[+_, +_]] = Parallel3[λ[(`-R`, `+E`, `+A`) => F[E, A]]]
  type Concurrent2[F[+_, +_]] = Concurrent3[λ[(`-R`, `+E`, `+A`) => F[E, A]]]
  type Async2[F[+_, +_]] = Async3[λ[(`-R`, `+E`, `+A`) => F[E, A]]]
  type Temporal2[F[+_, +_]] = Temporal3[λ[(`-R`, `+E`, `+A`) => F[E, A]]]

  type Fork2[F[+_, +_]] = Fork3[λ[(`-R`, `+E`, `+A`) => F[E, A]]]

  type Primitives3[F[-_, +_, +_]] = Primitives2[F[Any, +_, +_]]
  object Primitives3 {
    @inline def apply[F[-_, +_, +_]: Primitives3]: Primitives3[F] = implicitly
  }

  type PrimitivesM3[F[-_, +_, +_]] = PrimitivesM2[F[Any, +_, +_]]
  object PrimitivesM3 {
    @inline def apply[F[-_, +_, +_]: PrimitivesM3]: PrimitivesM3[F] = implicitly
  }

  type BlockingIO2[F[+_, +_]] = BlockingIO3[λ[(`-R`, `+E`, `+A`) => F[E, A]]]
  object BlockingIO2 {
    @inline def apply[F[+_, +_]: BlockingIO2]: BlockingIO2[F] = implicitly
  }

  type TransZio[F[_, _]] = Isomorphism2[F, zio.IO]
  object TransZio {
    @inline def apply[F[_, _]: TransZio]: TransZio[F] = implicitly
  }

  type Fiber3[+F[-_, +_, +_], +E, +A] = Fiber2[F[Any, +_, +_], E, A]
  lazy val Fiber3: Fiber2.type = Fiber2

  type RefM3[F[_, +_, +_], A] = RefM2[F[Any, +_, +_], A]
  lazy val RefM3: RefM2.type = RefM2

  type Mutex3[F[_, +_, +_], A] = Mutex2[F[Any, +_, +_]]
  lazy val Mutex3: Mutex2.type = Mutex2

  type Ref2[+F[_, _], A] = Ref1[F[Nothing, _], A]
  lazy val Ref2: Ref1.type = Ref1
  type Ref3[+F[_, _, _], A] = Ref1[F[Any, Nothing, _], A]
  lazy val Ref3: Ref1.type = Ref1

  type Promise3[+F[-_, +_, +_], E, A] = Promise2[F[Any, +_, +_], E, A]
  lazy val Promise3: Promise2.type = Promise2

  type Latch2[+F[+_, +_]] = Promise2[F, Nothing, Unit]
  lazy val Latch2: Promise2.type = Promise2
  type Latch3[+F[-_, +_, +_]] = Promise3[F, Nothing, Unit]
  lazy val Latch3: Promise2.type = Promise2

  type Semaphore2[+F[_, _]] = Semaphore1[F[Nothing, _]]
  lazy val Semaphore2: Semaphore1.type = Semaphore1
  type Semaphore3[+F[_, _, _]] = Semaphore1[F[Any, Nothing, _]]
  lazy val Semaphore3: Semaphore1.type = Semaphore1

  type UnsafeRun3[F[_, _, _]] = UnsafeRun2[F[Any, _, _]]
  object UnsafeRun3 {
    @inline def apply[F[_, _, _]: UnsafeRun3]: UnsafeRun3[F] = implicitly
  }

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

  @inline private[bio] final def cast3To2[C[_[-_, +_, +_]], FR[-_, +_, +_], R](
    instance: C[FR]
  ): C[λ[(`-R0`, `+E`, `+A`) => FR[R, E, A]]] = {
    instance.asInstanceOf[C[λ[(`-R0`, `+E`, `+A`) => FR[R, E, A]]]]
  }
}
