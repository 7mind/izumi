package izumi.functional

import izumi.functional.bio.PredefinedHelper.NotPredefined
import izumi.functional.bio.data.Isomorphism2
import izumi.functional.bio.retry.Scheduler2
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

  object CursedPower extends CursedPowerInstancesLowPriority1 {
    type FNothing[+_, +_] = Nothing
    type CursedPower <: Functor2[FNothing]
    val cursedManifestation: CursedPower = null.asInstanceOf[CursedPower]

//    @inline implicit final def ConvertFromConcurrent[F[+_, +_]](
//      @unused self: CursedPower
//    )(implicit Concurrent: NotPredefined.Of[Concurrent2[F]]
//    ): Predefined.Of[Panic2[F] & SpecificityHelper.S1] =
//      Predefined(SpecificityHelper.S1(Concurrent.InnerF))

    @inline implicit final def AttachPrimitives2[F[+_, +_]](@unused self: CursedPower)(implicit Primitives: Primitives2[F]): Primitives.type =
      Primitives
//    @inline implicit final def AttachScheduler[F[+_, +_]](@unused self: CursedPower)(implicit Scheduler: Scheduler2[F]): Scheduler.type = Scheduler
    @inline implicit final def AttachPrimitivesM[F[+_, +_]](@unused self: CursedPower)(implicit PrimitivesM: PrimitivesM2[F]): PrimitivesM.type =
      PrimitivesM
    @inline implicit final def AttachFork[F[+_, +_]](@unused self: CursedPower)(implicit Fork: Fork2[F]): Fork.type = Fork
    @inline implicit final def AttachBlockingIO[F[+_, +_]](@unused self: CursedPower)(implicit BlockingIO: BlockingIO2[F]): BlockingIO.type = BlockingIO
    @inline implicit final def AttachClockAccessor[F[+_, +_]](@unused self: CursedPower): Syntax2.ClockAccessor[F] = new Syntax2.ClockAccessor[F](false)
    @inline implicit final def AttachEntropyAccessor[F[+_, +_]](@unused self: CursedPower): Syntax2.EntropyAccessor[F] = new Syntax2.EntropyAccessor[F](false)

    // ???
    @inline implicit final def ConvertFromParallel[F[+_, +_]](
      @unused self: CursedPower
    )(implicit Parallel: NotPredefined.Of[Parallel2[F]]
    ): PredefinedHelper.Predefined.Of[Monad2[F] & SpecificityHelper.S3] =
      PredefinedHelper.Predefined(SpecificityHelper.S3(Parallel.InnerF))
  }
  import CursedPower.CursedPower
  sealed trait CursedPowerInstancesLowPriority1 extends CursedPowerInstancesLowPriority2 {
    @inline implicit final def AttachBifunctor[F[+_, +_]](@unused self: CursedPower)(implicit Bifunctor: Bifunctor2[F]): Bifunctor.type =
      Bifunctor
    @inline implicit final def AttachConcurrent[F[+_, +_]](@unused self: CursedPower)(implicit Concurrent: Concurrent2[F]): Concurrent.type =
      Concurrent
  }
  sealed trait CursedPowerInstancesLowPriority2 extends CursedPowerInstancesLowPriority3 {
    @inline implicit final def AttachParallel[F[+_, +_]](@unused self: CursedPower)(implicit Parallel: Parallel2[F]): Parallel.type = Parallel
  }
  sealed trait CursedPowerInstancesLowPriority3 extends CursedPowerInstancesLowPriority4 {
    @inline implicit final def AttachTemporal[F[+_, +_]](@unused self: CursedPower)(implicit Temporal: Temporal2[F]): Temporal2[F] = Temporal
  }
  sealed trait CursedPowerInstancesLowPriority4 {
//    @inline implicit final def AttachFunctor[F[+_, +_]](@unused self: CursedPower)(implicit Temporal: Functor2[F]): Functor2[F] = Temporal
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
