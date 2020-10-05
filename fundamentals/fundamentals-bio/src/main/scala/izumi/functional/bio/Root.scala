package izumi.functional.bio

import cats.data.Kleisli
import izumi.functional.bio.DivergenceHelper.{Divergent, Nondivergent}
import izumi.functional.bio.PredefinedHelper.{NotPredefined, Predefined}
import izumi.functional.bio.SpecificityHelper._
import izumi.functional.bio.impl.{AsyncMonix, AsyncZio}
import izumi.fundamentals.platform.language.unused
import zio.ZIO

import scala.language.implicitConversions
import izumi.fundamentals.orphans.`monix.bio.IO`

trait Root extends DivergenceHelper with PredefinedHelper

trait RootBifunctor[F[-_, +_, +_]] extends Root

trait RootTrifunctor[F[-_, +_, +_]] extends Root

object Root extends RootInstancesLowPriority1 {
  @inline implicit final def BIOConvertFromBIOConcurrent[FR[-_, +_, +_]](implicit BIOTemporal: NotPredefined.Of[Concurrent3[FR]]): Panic3[FR] with S1 =
    S1(BIOTemporal.InnerF)

  @inline implicit final def AttachBIOLocal[FR[-_, +_, +_], R](@unused self: Functor3[FR])(implicit BIOLocal: Local3[FR]): BIOLocal.type = BIOLocal
  @inline implicit final def AttachBIOPrimitives3[FR[-_, +_, +_]](@unused self: Functor3[FR])(implicit BIOPrimitives: BIOPrimitives3[FR]): BIOPrimitives.type =
    BIOPrimitives
  @inline implicit final def AttachBIOFork3[FR[-_, +_, +_]](@unused self: Functor3[FR])(implicit BIOFork: Fork3[FR]): BIOFork.type = BIOFork
  @inline implicit final def AttachBlockingIO3[FR[-_, +_, +_]](@unused self: Functor3[FR])(implicit BlockingIO: BlockingIO3[FR]): BlockingIO.type = BlockingIO

  implicit final class KleisliSyntaxAttached[FR[-_, +_, +_]](private val FR0: Functor3[FR]) extends AnyVal {
    @inline def fromKleisli[R, E, A](k: Kleisli[FR[Any, E, ?], R, A])(implicit FR: MonadAsk3[FR]): FR[R, E, A] = FR.fromKleisli(k)
    @inline def toKleisli[R, E, A](fr: FR[R, E, A])(implicit FR: Local3[FR]): Kleisli[FR[Any, E, ?], R, A] = {
      val _ = FR0
      FR.toKleisli(fr)
    }
  }
}

sealed trait RootInstancesLowPriority1 extends RootInstancesLowPriority2 {
  @inline implicit final def BIOConvertFromBIOTemporal[FR[-_, +_, +_]](implicit BIOTemporal: NotPredefined.Of[Temporal3[FR]]): Error3[FR] with S2 =
    S2(BIOTemporal.InnerF)

  @inline implicit final def AttachBIOArrowChoice[FR[-_, +_, +_]](@unused self: Functor3[FR])(implicit BIOArrowChoice: ArrowChoice3[FR]): BIOArrowChoice.type =
    BIOArrowChoice
  @inline implicit final def AttachBIOMonadAsk[FR[-_, +_, +_], R](@unused self: Functor3[FR])(implicit BIOMonadAsk: MonadAsk3[FR]): BIOMonadAsk.type = BIOMonadAsk
  @inline implicit final def AttachBIOConcurrent[FR[-_, +_, +_], R](@unused self: Concurrent3[FR])(implicit BIOConcurrent: Concurrent3[FR]): BIOConcurrent.type =
    BIOConcurrent
}

sealed trait RootInstancesLowPriority2 extends RootInstancesLowPriority3 {
  @inline implicit final def BIOConvertFromBIOParallel[FR[-_, +_, +_]](implicit BIOParallel: NotPredefined.Of[Parallel3[FR]]): Monad3[FR] with S3 =
    S3(BIOParallel.InnerF)

  @inline implicit final def AttachBIOArrow[FR[-_, +_, +_]](@unused self: Functor3[FR])(implicit BIOArrow: BIOArrow[FR]): BIOArrow.type = BIOArrow
  @inline implicit final def AttachBIOBifunctor[FR[-_, +_, +_], R](@unused self: Functor3[FR])(implicit BIOBifunctor: Bifunctor3[FR]): BIOBifunctor.type =
    BIOBifunctor
  @inline implicit final def AttachBIOConcurrent[FR[-_, +_, +_], R](@unused self: Functor3[FR])(implicit BIOConcurrent: Concurrent3[FR]): BIOConcurrent.type =
    BIOConcurrent
}

sealed trait RootInstancesLowPriority3 extends RootInstancesLowPriority4 {
  @inline implicit final def BIOConvertFromBIOMonadAsk[FR[-_, +_, +_]](implicit BIOMonadAsk: NotPredefined.Of[MonadAsk3[FR]]): Monad3[FR] with S4 =
    S4(BIOMonadAsk.InnerF)

  @inline implicit final def AttachBIOAsk[FR[-_, +_, +_], R](@unused self: Functor3[FR])(implicit BIOAsk: Ask3[FR]): BIOAsk.type = BIOAsk
  @inline implicit final def AttachBIOProfunctor[FR[-_, +_, +_]](@unused self: Functor3[FR])(implicit BIOProfunctor: Profunctor3[FR]): BIOProfunctor.type =
    BIOProfunctor
  @inline implicit final def AttachBIOParallel[FR[-_, +_, +_], R](@unused self: Functor3[FR])(implicit BIOParallel: Parallel3[FR]): BIOParallel.type = BIOParallel
}

sealed trait RootInstancesLowPriority4 extends RootInstancesLowPriority5 {
  @inline implicit final def BIOConvertFromBIOAsk[FR[-_, +_, +_]](implicit BIOAsk: NotPredefined.Of[Ask3[FR]]): Applicative3[FR] with S5 = S5(BIOAsk.InnerF)

  @inline implicit final def AttachBIOTemporal[FR[-_, +_, +_], R](@unused self: Functor3[FR])(implicit BIOTemporal: Temporal3[FR]): BIOTemporal.type = BIOTemporal
}

sealed trait RootInstancesLowPriority5 extends RootInstancesLowPriority6 {
  @inline implicit final def BIOConvertFromBIOProfunctor[FR[-_, +_, +_]](implicit BIOProfunctor: NotPredefined.Of[Profunctor3[FR]]): Functor3[FR] with S6 =
    S6(BIOProfunctor.InnerF)
}

sealed trait RootInstancesLowPriority6 extends RootInstancesLowPriority7 {
  @inline implicit final def BIOConvertFromBIOBifunctor[FR[-_, +_, +_]](implicit BIOBifunctor: NotPredefined.Of[Bifunctor3[FR]]): Functor3[FR] with S7 =
    S7(BIOBifunctor.InnerF)
}

sealed trait RootInstancesLowPriority7 extends RootInstancesLowPriority8 {
  @inline implicit final def BIOLocalZIO: Predefined.Of[Local3[ZIO]] = Predefined(AsyncZio)
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
  // seems like it's because of the type lambda in `BIOAsync` definition
  @inline implicit final def BIOMonix[MonixBIO[+_, +_]](implicit @unused M: `monix.bio.IO`[MonixBIO]): Predefined.Of[BIOAsync[MonixBIO]] =
    AsyncMonix.asInstanceOf[Predefined.Of[BIOAsync[MonixBIO]]]

}

sealed trait RootInstancesLowPriority9 {
  @inline implicit final def BIOConvert3To2[C[f[-_, +_, +_]] <: DivergenceHelper with RootBifunctor[f], FR[-_, +_, +_], R0](
    implicit BIOFunctor3: C[FR] { type Divergence = Nondivergent }
  ): C[Lambda[(`-R`, `+E`, `+A`) => FR[R0, E, A]]] with DivergenceHelper { type Divergence = Divergent } =
    Divergent(cast3To2[C, FR, R0](BIOFunctor3))
}
