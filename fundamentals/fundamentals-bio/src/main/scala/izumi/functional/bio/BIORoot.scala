package izumi.functional.bio

import cats.data.Kleisli
import izumi.functional.bio.DivergenceHelper.{Divergent, Nondivergent}
import izumi.functional.bio.PredefinedHelper.{NotPredefined, Predefined}
import izumi.functional.bio.SpecificityHelper._
import izumi.functional.bio.impl.{BIOAsyncMonix, BIOAsyncZio}
import izumi.fundamentals.platform.language.unused
import zio.ZIO

import scala.language.implicitConversions
import izumi.fundamentals.orphans.`monix.bio.IO`

trait BIORoot extends DivergenceHelper with PredefinedHelper

trait BIORootBifunctor[F[-_, +_, +_]] extends BIORoot

trait BIORootTrifunctor[F[-_, +_, +_]] extends BIORoot

object BIORoot extends BIORootInstancesLowPriority1 {
  @inline implicit final def BIOConvertFromBIOConcurrent[FR[-_, +_, +_]](implicit BIOTemporal: NotPredefined.Of[BIOConcurrent3[FR]]): BIOPanic3[FR] with S1 =
    S1(BIOTemporal.InnerF)

  @inline implicit final def AttachBIOLocal[FR[-_, +_, +_], R](@unused self: BIOFunctor3[FR])(implicit BIOLocal: BIOLocal[FR]): BIOLocal.type = BIOLocal
  @inline implicit final def AttachBIOPrimitives3[FR[-_, +_, +_]](@unused self: BIOFunctor3[FR])(implicit BIOPrimitives: BIOPrimitives3[FR]): BIOPrimitives.type =
    BIOPrimitives
  @inline implicit final def AttachBIOFork3[FR[-_, +_, +_]](@unused self: BIOFunctor3[FR])(implicit BIOFork: BIOFork3[FR]): BIOFork.type = BIOFork
  @inline implicit final def AttachBlockingIO3[FR[-_, +_, +_]](@unused self: BIOFunctor3[FR])(implicit BlockingIO: BlockingIO3[FR]): BlockingIO.type = BlockingIO

  implicit final class KleisliSyntaxAttached[FR[-_, +_, +_]](@unused private val FR0: BIOFunctor3[FR]) extends AnyVal {
    @inline def fromKleisli[R, E, A](k: Kleisli[FR[Any, E, ?], R, A])(implicit FR: BIOMonadAsk[FR]): FR[R, E, A] = FR.fromKleisli(k)
    @inline def toKleisli[R, E, A](fr: FR[R, E, A])(implicit FR: BIOLocal[FR]): Kleisli[FR[Any, E, ?], R, A] = FR.toKleisli(fr)
  }
}

sealed trait BIORootInstancesLowPriority1 extends BIORootInstancesLowPriority2 {
  @inline implicit final def BIOConvertFromBIOTemporal[FR[-_, +_, +_]](implicit BIOTemporal: NotPredefined.Of[BIOTemporal3[FR]]): BIOError3[FR] with S2 =
    S2(BIOTemporal.InnerF)

  @inline implicit final def AttachBIOArrowChoice[FR[-_, +_, +_]](@unused self: BIOFunctor3[FR])(implicit BIOArrowChoice: BIOArrowChoice[FR]): BIOArrowChoice.type =
    BIOArrowChoice
  @inline implicit final def AttachBIOMonadAsk[FR[-_, +_, +_], R](@unused self: BIOFunctor3[FR])(implicit BIOMonadAsk: BIOMonadAsk[FR]): BIOMonadAsk.type = BIOMonadAsk
  @inline implicit final def AttachBIOConcurrent[FR[-_, +_, +_], R](@unused self: BIOConcurrent3[FR])(implicit BIOConcurrent: BIOConcurrent3[FR]): BIOConcurrent.type =
    BIOConcurrent
}

sealed trait BIORootInstancesLowPriority2 extends BIORootInstancesLowPriority3 {
  @inline implicit final def BIOConvertFromBIOParallel[FR[-_, +_, +_]](implicit BIOParallel: NotPredefined.Of[BIOParallel3[FR]]): BIOMonad3[FR] with S3 =
    S3(BIOParallel.InnerF)

  @inline implicit final def AttachBIOArrow[FR[-_, +_, +_]](@unused self: BIOFunctor3[FR])(implicit BIOArrow: BIOArrow[FR]): BIOArrow.type = BIOArrow
  @inline implicit final def AttachBIOBifunctor[FR[-_, +_, +_], R](@unused self: BIOFunctor3[FR])(implicit BIOBifunctor: BIOBifunctor3[FR]): BIOBifunctor.type =
    BIOBifunctor
  @inline implicit final def AttachBIOConcurrent[FR[-_, +_, +_], R](@unused self: BIOFunctor3[FR])(implicit BIOConcurrent: BIOConcurrent3[FR]): BIOConcurrent.type =
    BIOConcurrent
}

sealed trait BIORootInstancesLowPriority3 extends BIORootInstancesLowPriority4 {
  @inline implicit final def BIOConvertFromBIOMonadAsk[FR[-_, +_, +_]](implicit BIOMonadAsk: NotPredefined.Of[BIOMonadAsk[FR]]): BIOMonad3[FR] with S4 =
    S4(BIOMonadAsk.InnerF)

  @inline implicit final def AttachBIOAsk[FR[-_, +_, +_], R](@unused self: BIOFunctor3[FR])(implicit BIOAsk: BIOAsk[FR]): BIOAsk.type = BIOAsk
  @inline implicit final def AttachBIOProfunctor[FR[-_, +_, +_]](@unused self: BIOFunctor3[FR])(implicit BIOProfunctor: BIOProfunctor[FR]): BIOProfunctor.type =
    BIOProfunctor
  @inline implicit final def AttachBIOParallel[FR[-_, +_, +_], R](@unused self: BIOFunctor3[FR])(implicit BIOParallel: BIOParallel3[FR]): BIOParallel.type = BIOParallel
}

sealed trait BIORootInstancesLowPriority4 extends BIORootInstancesLowPriority5 {
  @inline implicit final def BIOConvertFromBIOAsk[FR[-_, +_, +_]](implicit BIOAsk: NotPredefined.Of[BIOAsk[FR]]): BIOApplicative3[FR] with S5 = S5(BIOAsk.InnerF)

  @inline implicit final def AttachBIOTemporal[FR[-_, +_, +_], R](@unused self: BIOFunctor3[FR])(implicit BIOTemporal: BIOTemporal3[FR]): BIOTemporal.type = BIOTemporal
}

sealed trait BIORootInstancesLowPriority5 extends BIORootInstancesLowPriority6 {
  @inline implicit final def BIOConvertFromBIOProfunctor[FR[-_, +_, +_]](implicit BIOProfunctor: NotPredefined.Of[BIOProfunctor[FR]]): BIOFunctor3[FR] with S6 =
    S6(BIOProfunctor.InnerF)
}

sealed trait BIORootInstancesLowPriority6 extends BIORootInstancesLowPriority7 {
  @inline implicit final def BIOConvertFromBIOBifunctor[FR[-_, +_, +_]](implicit BIOBifunctor: NotPredefined.Of[BIOBifunctor3[FR]]): BIOFunctor3[FR] with S7 =
    S7(BIOBifunctor.InnerF)
}

sealed trait BIORootInstancesLowPriority7 extends BIORootInstancesLowPriority8 {
  @inline implicit final def BIOLocalZIO: Predefined.Of[BIOLocal[ZIO]] = Predefined(BIOAsyncZio)
  @inline implicit final def BIOZIO: Predefined.Of[BIOAsync3[ZIO]] = Predefined(BIOAsyncZio)
}

sealed trait BIORootInstancesLowPriority8 extends BIORootInstancesLowPriority9 {
  /**
    * This instance uses 'no more orphans' trick to provide an Optional instance
    * only IFF you have monix-bio as a dependency without REQUIRING a monix-bio dependency.
    *
    * Optional instance via https://blog.7mind.io/no-more-orphans.html
    */
  // for some reason ZIO instances do not require no-more-orphans machinery and do not create errors when zio is not on classpath...
  // seems like it's because of the type lambda in `BIOAsync` definition
  @inline implicit final def BIOMonix[MonixBIO[+_, +_]](implicit @unused M: `monix.bio.IO`[MonixBIO]): Predefined.Of[BIOAsync[MonixBIO]] =
    BIOAsyncMonix.asInstanceOf[Predefined.Of[BIOAsync[MonixBIO]]]

}

sealed trait BIORootInstancesLowPriority9 {
  @inline implicit final def BIOConvert3To2[C[f[-_, +_, +_]] <: DivergenceHelper with BIORootBifunctor[f], FR[-_, +_, +_], R0](
    implicit BIOFunctor3: C[FR] { type Divergence = Nondivergent }
  ): C[Lambda[(`-R`, `+E`, `+A`) => FR[R0, E, A]]] with DivergenceHelper { type Divergence = Divergent } =
    Divergent(cast3To2[C, FR, R0](BIOFunctor3))
}
