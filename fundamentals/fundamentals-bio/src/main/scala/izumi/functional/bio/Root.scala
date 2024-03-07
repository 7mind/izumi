package izumi.functional.bio

import izumi.functional.bio.PredefinedHelper.{NotPredefined, Predefined}
import izumi.functional.bio.SpecificityHelper.*
import izumi.functional.bio.impl.{AsyncZio, BioEither, BioIdentity2}
import izumi.functional.bio.retry.Scheduler2
import izumi.functional.bio.syntax.Syntax2
import izumi.fundamentals.orphans.`zio.ZIO`
import izumi.fundamentals.platform.functional.Identity2

import scala.annotation.unused
import scala.language.implicitConversions

trait RootBifunctor[F[+_, +_]] extends Root

trait Root extends DivergenceHelper with PredefinedHelper

object Root extends RootInstancesLowPriority1 {
  @inline implicit final def ConvertFromConcurrent[F[+_, +_]](implicit Concurrent: NotPredefined.Of[Concurrent2[F]]): Predefined.Of[Panic2[F] & S1] =
    Predefined(S1(Concurrent.InnerF))

  @inline implicit final def AttachPrimitives[F[+_, +_]](@unused self: Functor2[F])(implicit Primitives: Primitives2[F]): Primitives.type =
    Primitives
  @inline implicit final def AttachScheduler[F[+_, +_]](@unused self: Functor2[F])(implicit Scheduler: Scheduler2[F]): Scheduler.type = Scheduler
  @inline implicit final def AttachPrimitivesM[F[+_, +_]](@unused self: Functor2[F])(implicit PrimitivesM: PrimitivesM2[F]): PrimitivesM.type =
    PrimitivesM
  @inline implicit final def AttachFork[F[+_, +_]](@unused self: Functor2[F])(implicit Fork: Fork2[F]): Fork.type = Fork
  @inline implicit final def AttachBlockingIO[F[+_, +_]](@unused self: Functor2[F])(implicit BlockingIO: BlockingIO2[F]): BlockingIO.type = BlockingIO

  @inline implicit final def AttachClockAccessor[F[+_, +_]](@unused self: Functor2[F]): Syntax2.ClockAccessor[F] = new Syntax2.ClockAccessor[F](false)
  @inline implicit final def AttachEntropyAccessor[F[+_, +_]](@unused self: Functor2[F]): Syntax2.EntropyAccessor[F] = new Syntax2.EntropyAccessor[F](false)
}

sealed trait RootInstancesLowPriority1 extends RootInstancesLowPriority2 {
  @inline implicit final def ConvertFromTemporal[F[+_, +_]](implicit Temporal: NotPredefined.Of[Temporal2[F]]): Predefined.Of[Error2[F] & S2] =
    Predefined(S2(Temporal.InnerF))
  @inline implicit final def AttachBifunctor[F[+_, +_]](@unused self: Functor2[F])(implicit Bifunctor: Bifunctor2[F]): Bifunctor.type =
    Bifunctor
  @inline implicit final def AttachConcurrent[F[+_, +_]](@unused self: Functor2[F])(implicit Concurrent: Concurrent2[F]): Concurrent.type =
    Concurrent
}

sealed trait RootInstancesLowPriority2 extends RootInstancesLowPriority3 {
  @inline implicit final def ConvertFromParallel[F[+_, +_]](implicit Parallel: NotPredefined.Of[Parallel2[F]]): Predefined.Of[Monad2[F] & S3] =
    Predefined(S3(Parallel.InnerF))

  @inline implicit final def AttachParallel[F[+_, +_]](@unused self: Functor2[F])(implicit Parallel: Parallel2[F]): Parallel.type = Parallel
}

sealed trait RootInstancesLowPriority3 extends RootInstancesLowPriority8 {
  @inline implicit final def ConvertFromBifunctor[F[+_, +_]](implicit Bifunctor: NotPredefined.Of[Bifunctor2[F]]): Predefined.Of[Functor2[F] & S7] =
    Predefined(S7(Bifunctor.InnerF))

  @inline implicit final def AttachTemporal[F[+_, +_]](@unused self: Functor2[F])(implicit Temporal: Temporal2[F]): Temporal2[F] = Temporal
}

sealed trait RootInstancesLowPriority4 extends RootInstancesLowPriority5 {
  /**
    * This instance uses 'no more orphans' trick to provide an Optional instance
    * only IFF you have zio-core as a dependency without REQUIRING a zio-core dependency.
    *
    * Optional instance via https://blog.7mind.io/no-more-orphans.html
    */
  // since removing trifunctor, ZIO instances now also require no-more-orphans machinery
  @inline implicit final def BIOZIO[ZIO[-_, +_, +_]: `zio.ZIO`]: Predefined.Of[Async2[ZIO[Any, +_, +_]]] = Predefined(AsyncZio.asInstanceOf[Async2[ZIO[Any, +_, +_]]])
}

sealed trait RootInstancesLowPriority5 extends RootInstancesLowPriority6 {
  @inline implicit final def BIOZIOR[ZIO[-_, +_, +_]: `zio.ZIO`, R]: Predefined.Of[Async2[ZIO[R, +_, +_]]] = Predefined(AsyncZio.asInstanceOf[Async2[ZIO[R, +_, +_]]])
}

sealed trait RootInstancesLowPriority6 extends RootInstancesLowPriority7 {
  /**
    * This instance uses 'no more orphans' trick to provide an Optional instance
    * only IFF you have monix-bio as a dependency without REQUIRING a monix-bio dependency.
    *
    * Optional instance via https://blog.7mind.io/no-more-orphans.html
    */
  // for some reason ZIO instances do not require no-more-orphans machinery and do not create errors when zio is not on classpath...
  // seems like it's because of the additional type lambda in `Async2` definition, unlike `Async3`
//  @inline implicit final def BIOMonix[MonixBIO[+_, +_]](implicit @unused M: `monix.bio.IO`[MonixBIO]): Predefined.Of[Async2[MonixBIO]] =
//    AsyncMonix.asInstanceOf[Predefined.Of[Async2[MonixBIO]]]
}

sealed trait RootInstancesLowPriority7 extends RootInstancesLowPriority8 {
  @inline implicit final def BIOEither: Predefined.Of[Error2[Either]] = Predefined(BioEither)
}

sealed trait RootInstancesLowPriority8 {
  @inline implicit final def BIOIdentity2: Predefined.Of[Monad2[Identity2]] = BioIdentity2.asInstanceOf[Predefined.Of[Monad2[Identity2]]]
}
