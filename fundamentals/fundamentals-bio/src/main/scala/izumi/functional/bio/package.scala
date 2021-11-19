package izumi.functional

import izumi.functional.bio.data.Isomorphism2
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

  type Primitives3[F[-_, +_, +_]] = Primitives2[F[Any, + _, + _]]
  object Primitives3 {
    @inline def apply[F[-_, +_, +_]: Primitives3]: Primitives3[F] = implicitly
  }

  type PrimitivesM3[F[-_, +_, +_]] = PrimitivesM2[F[Any, + _, + _]]
  object PrimitivesM3 {
    @inline def apply[F[-_, +_, +_]: PrimitivesM3]: PrimitivesM3[F] = implicitly
  }

  type BlockingIO2[F[+_, +_]] = BlockingIO3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  object BlockingIO2 {
    @inline def apply[F[+_, +_]: BlockingIO2]: BlockingIO2[F] = implicitly
  }

  type TransZio[F[_, _]] = Isomorphism2[F, zio.IO]
  object TransZio {
    @inline def apply[F[_, _]: TransZio]: TransZio[F] = implicitly
  }

  type Fiber3[+F[-_, +_, +_], +E, +A] = Fiber2[F[Any, + _, + _], E, A]
  lazy val Fiber3: Fiber2.type = Fiber2

  type RefM3[F[_, +_, +_], A] = RefM2[F[Any, + _, + _], A]
  lazy val RefM3: RefM2.type = RefM2

  type Mutex3[F[_, +_, +_], A] = Mutex2[F[Any, + _, + _]]
  lazy val Mutex3: Mutex2.type = Mutex2

  type Ref2[+F[_, _], A] = Ref1[F[Nothing, _], A]
  lazy val Ref2: Ref1.type = Ref1
  type Ref3[+F[_, _, _], A] = Ref1[F[Any, Nothing, _], A]
  lazy val Ref3: Ref1.type = Ref1

  type Promise3[+F[-_, +_, +_], E, A] = Promise2[F[Any, + _, + _], E, A]
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

  type SyncSafe2[F[_, _]] = SyncSafe[F[Nothing, _]]
  object SyncSafe2 {
    @inline def apply[F[_, _]: SyncSafe2]: SyncSafe2[F] = implicitly
  }
  type SyncSafe3[F[_, _, _]] = SyncSafe[F[Any, Nothing, _]]
  object SyncSafe3 {
    @inline def apply[F[_, _, _]: SyncSafe3]: SyncSafe3[F] = implicitly
  }

  type Clock2[F[_, _]] = Clock[F[Nothing, _]]
  object Clock2 {
    @inline def apply[F[_, _]: Clock2]: Clock2[F] = implicitly
  }
  type Clock3[F[_, _, _]] = Clock[F[Any, Nothing, _]]
  object Clock3 {
    @inline def apply[F[_, _, _]: Clock3]: Clock3[F] = implicitly
  }

  type Entropy2[F[_, _]] = Entropy[F[Nothing, _]]
  object Entropy2 {
    @inline def apply[F[_, _]: Entropy2]: Entropy2[F] = implicitly
  }
  type Entropy3[F[_, _, _]] = Entropy[F[Any, Nothing, _]]
  object Entropy3 {
    @inline def apply[F[_, _, _]: Entropy3]: Entropy3[F] = implicitly
  }

  @inline private[bio] final def cast3To2[C[_[-_, +_, +_]], FR[-_, +_, +_], R](
    instance: C[FR]
  ): C[Lambda[(`-R0`, `+E`, `+A`) => FR[R, E, A]]] = {
    instance.asInstanceOf[C[Lambda[(`-R0`, `+E`, `+A`) => FR[R, E, A]]]]
  }

}

package bio {
  import izumi.fundamentals.platform.language.unused

  trait Functor3[F[-_, +_, +_]] extends RootBifunctor[F] {
    def map[R, E, A, B](r: F[R, E, A])(f: A => B): F[R, E, B]

    def as[R, E, A, B](r: F[R, E, A])(v: => B): F[R, E, B] = map(r)(_ => v)
    def void[R, E, A](r: F[R, E, A]): F[R, E, Unit] = map(r)(_ => ())

    /** Extracts the optional value, or returns the given `valueOnNone` value */
    def fromOptionOr[R, E, A](valueOnNone: => A, r: F[R, E, Option[A]]): F[R, E, A] = map(r)(_.getOrElse(valueOnNone))

    @inline final def widen[R, E, A, A1](r: F[R, E, A])(implicit @unused ev: A <:< A1): F[R, E, A1] = r.asInstanceOf[F[R, E, A1]]
  }

  import cats.data.Kleisli
  import izumi.functional.bio.DivergenceHelper.{Divergent, Nondivergent}
  import izumi.functional.bio.PredefinedHelper.{NotPredefined, Predefined}
  import izumi.functional.bio.SpecificityHelper._
  import izumi.functional.bio.impl.{AsyncMonix, AsyncZio, BioEither, BioIdentity3}
  import izumi.functional.bio.retry.Scheduler3
  import izumi.fundamentals.orphans.`monix.bio.IO`
  import izumi.fundamentals.platform.functional.{Identity2, Identity3}
  import zio.ZIO

  import scala.language.implicitConversions

  trait Root extends DivergenceHelper with PredefinedHelper

  trait RootBifunctor[F[-_, +_, +_]] extends Root

  trait RootTrifunctor[F[-_, +_, +_]] extends Root

  object Root extends RootInstancesLowPriority1 {
    @inline implicit final def ConvertFromConcurrent[FR[-_, +_, +_]](implicit Concurrent: NotPredefined.Of[Concurrent3[FR]]): Panic3[FR] with S1 =
      S1(Concurrent.InnerF)

    @inline implicit final def AttachLocal[FR[-_, +_, +_], R](@unused self: Functor3[FR])(implicit Local: Local3[FR]): Local.type = Local
    @inline implicit final def AttachPrimitives3[FR[-_, +_, +_]](@unused self: Functor3[FR])(implicit Primitives: Primitives3[FR]): Primitives.type =
      Primitives
    @inline implicit final def AttachScheduler3[FR[-_, +_, +_]](@unused self: Functor3[FR])(implicit Scheduler: Scheduler3[FR]): Scheduler.type = Scheduler
    @inline implicit final def AttachPrimitivesM3[FR[-_, +_, +_]](@unused self: Functor3[FR])(implicit PrimitivesM: PrimitivesM3[FR]): PrimitivesM.type =
      PrimitivesM
    @inline implicit final def AttachFork3[FR[-_, +_, +_]](@unused self: Functor3[FR])(implicit Fork: Fork3[FR]): Fork.type = Fork
    @inline implicit final def AttachBlockingIO3[FR[-_, +_, +_]](@unused self: Functor3[FR])(implicit BlockingIO: BlockingIO3[FR]): BlockingIO.type = BlockingIO

    implicit final class KleisliSyntaxAttached[FR[-_, +_, +_]](private val FR0: Functor3[FR]) extends AnyVal {
      @inline def fromKleisli[R, E, A](k: Kleisli[FR[Any, E, _], R, A])(implicit FR: MonadAsk3[FR]): FR[R, E, A] = FR.fromKleisli(k)
      @inline def toKleisli[R, E, A](fr: FR[R, E, A])(implicit FR: Local3[FR]): Kleisli[FR[Any, E, _], R, A] = {
        val _ = FR0
        FR.toKleisli(fr)
      }
    }
  }

  sealed trait RootInstancesLowPriority1 extends RootInstancesLowPriority2 {
    @inline implicit final def ConvertFromTemporal[FR[-_, +_, +_]](implicit Temporal: NotPredefined.Of[Temporal3[FR]]): Error3[FR] with S2 =
      S2(Temporal.InnerF)

    @inline implicit final def AttachArrowChoice[FR[-_, +_, +_]](@unused self: Functor3[FR])(implicit ArrowChoice: ArrowChoice3[FR]): ArrowChoice.type =
      ArrowChoice
    @inline implicit final def AttachMonadAsk[FR[-_, +_, +_], R](@unused self: Functor3[FR])(implicit MonadAsk: MonadAsk3[FR]): MonadAsk.type = MonadAsk
    @inline implicit final def AttachConcurrent[FR[-_, +_, +_], R](@unused self: Concurrent3[FR])(implicit Concurrent: Concurrent3[FR]): Concurrent.type =
      Concurrent
  }

  sealed trait RootInstancesLowPriority2 extends RootInstancesLowPriority3 {
    @inline implicit final def ConvertFromParallel[FR[-_, +_, +_]](implicit Parallel: NotPredefined.Of[Parallel3[FR]]): Monad3[FR] with S3 =
      S3(Parallel.InnerF)

    @inline implicit final def AttachArrow[FR[-_, +_, +_]](@unused self: Functor3[FR])(implicit Arrow: Arrow3[FR]): Arrow.type = Arrow
    @inline implicit final def AttachBifunctor[FR[-_, +_, +_], R](@unused self: Functor3[FR])(implicit Bifunctor: Bifunctor3[FR]): Bifunctor.type =
      Bifunctor
    @inline implicit final def AttachConcurrent[FR[-_, +_, +_], R](@unused self: Functor3[FR])(implicit Concurrent: Concurrent3[FR]): Concurrent.type =
      Concurrent
  }

  sealed trait RootInstancesLowPriority3 extends RootInstancesLowPriority4 {
    @inline implicit final def ConvertFromMonadAsk[FR[-_, +_, +_]](implicit MonadAsk: NotPredefined.Of[MonadAsk3[FR]]): Monad3[FR] with S4 =
      S4(MonadAsk.InnerF)

    @inline implicit final def AttachAsk[FR[-_, +_, +_], R](@unused self: Functor3[FR])(implicit Ask: Ask3[FR]): Ask.type = Ask
    @inline implicit final def AttachProfunctor[FR[-_, +_, +_]](@unused self: Functor3[FR])(implicit Profunctor: Profunctor3[FR]): Profunctor.type =
      Profunctor
    @inline implicit final def AttachParallel[FR[-_, +_, +_], R](@unused self: Functor3[FR])(implicit Parallel: Parallel3[FR]): Parallel.type = Parallel
  }

  sealed trait RootInstancesLowPriority4 extends RootInstancesLowPriority5 {
    @inline implicit final def ConvertFromAsk[FR[-_, +_, +_]](implicit Ask: NotPredefined.Of[Ask3[FR]]): Applicative3[FR] with S5 = S5(Ask.InnerF)

    @inline implicit final def AttachTemporal[FR[-_, +_, +_], R](@unused self: Functor3[FR])(implicit Temporal: Temporal3[FR]): Temporal.type = Temporal
  }

  sealed trait RootInstancesLowPriority5 extends RootInstancesLowPriority6 {
    @inline implicit final def ConvertFromProfunctor[FR[-_, +_, +_]](implicit Profunctor: NotPredefined.Of[Profunctor3[FR]]): Functor3[FR] with S6 =
      S6(Profunctor.InnerF)
  }

  sealed trait RootInstancesLowPriority6 extends RootInstancesLowPriority7 {
    @inline implicit final def ConvertFromBifunctor[FR[-_, +_, +_]](implicit Bifunctor: NotPredefined.Of[Bifunctor3[FR]]): Functor3[FR] with S7 =
      S7(Bifunctor.InnerF)
  }

  sealed trait RootInstancesLowPriority7 extends RootInstancesLowPriority8 {
    @inline implicit final def Local3ZIO: Predefined.Of[Local3[ZIO]] = Predefined(AsyncZio)
    @inline implicit final def BIOZIO: Predefined.Of[Async3[ZIO]] = Predefined(AsyncZio)
  }

  sealed trait RootInstancesLowPriority8 extends RootInstancesLowPriority9 {
    /**
      * This instance uses 'no more orphans' trick to provide an Optional instance
      * only IFF you have monix-bio as a dependency without REQUIRING a monix-bio dependency.
      *
      * Optional instance via https://blog.7mind.io/no-more-orphans.html
      */
    // for some reason ZIO instances do not require no-more-orphans machinery and do not create errors when zio is not on classpath...
    // seems like it's because of the type lambda in `Async2` definition
    @inline implicit final def BIOMonix[MonixBIO[+_, +_]](implicit @unused M: `monix.bio.IO`[MonixBIO]): Predefined.Of[Async2[MonixBIO]] =
      AsyncMonix.asInstanceOf[Predefined.Of[Async2[MonixBIO]]]
  }

  sealed trait RootInstancesLowPriority9 extends RootInstancesLowPriority10 {
    @inline implicit final def BIOEither: Predefined.Of[Error2[Either]] = Predefined(BioEither)
  }

  sealed trait RootInstancesLowPriority10 extends RootInstancesLowPriority11 {
    @inline implicit final def BIOIdentity2: Predefined.Of[Monad2[Identity2]] = BioIdentity3.asInstanceOf[Predefined.Of[Monad2[Identity2]]]
    @inline implicit final def BIOIdentity3: Predefined.Of[Monad3[Identity3]] = Predefined(BioIdentity3)
  }

  sealed trait RootInstancesLowPriority11 {
    @inline implicit final def Convert3To2[C[f[-_, +_, +_]] <: DivergenceHelper with RootBifunctor[f], FR[-_, +_, +_], R0](
      implicit BifunctorPlus: C[FR] { type Divergence = Nondivergent }
    ): C[Lambda[(`-R`, `+E`, `+A`) => FR[R0, E, A]]] with DivergenceHelper { type Divergence = Divergent } =
      Divergent(cast3To2[C, FR, R0](BifunctorPlus))
  }
}
