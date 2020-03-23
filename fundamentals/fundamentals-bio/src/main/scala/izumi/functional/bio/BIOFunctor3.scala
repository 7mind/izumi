package izumi.functional.bio

import cats.data.Kleisli
import izumi.functional.bio.DivergenceHelper.{Divergent, Nondivergent}
import izumi.functional.bio.PredefinedHelper.{NotPredefined, Predefined}
import izumi.functional.bio.SpecificityHelper.{S1, S2, S3, S4}
import izumi.functional.bio.impl.BIOAsyncZio
import zio.ZIO

import scala.language.implicitConversions

trait BIOFunctor3[F[-_, +_, +_]] extends BIOFunctorInstances with DivergenceHelper with PredefinedHelper {
  def map[R, E, A, B](r: F[R, E, A])(f: A => B): F[R, E, B]

  def as[R, E, A, B](r: F[R, E, A])(v: => B): F[R, E, B] = map(r)(_ => v)
  def void[R, E, A](r: F[R, E, A]): F[R, E, Unit] = map(r)(_ => ())
  @inline final def widen[R, E, A, A1](r: F[R, E, A])(implicit @deprecated("unused", "") ev: A <:< A1): F[R, E, A1] = r.asInstanceOf[F[R, E, A1]]
}

private[bio] sealed trait BIOFunctorInstances
object BIOFunctorInstances extends BIOFunctorInstancesLowPriority1 {
  @inline implicit final def BIOConvertFromBIOMonadAsk[FR[-_, +_, +_]](implicit BIOMonadAsk: NotPredefined.Of[BIOMonadAsk[FR]]): BIOMonad3[FR] with S1 = S1(BIOMonadAsk.InnerF)

  @inline implicit final def AttachBIOLocal[FR[-_, +_, +_], R](@deprecated("unused", "") self: BIOFunctor3[FR])(implicit BIOLocal: BIOLocal[FR]): BIOLocal.type = BIOLocal
  @inline implicit final def AttachBIOPrimitives3[FR[-_, +_, +_]](@deprecated("unused", "") self: BIOFunctor3[FR])(implicit BIOPrimitives: BIOPrimitives3[FR]): BIOPrimitives.type = BIOPrimitives
  @inline implicit final def AttachBIOFork3[FR[-_, +_, +_]](@deprecated("unused", "") self: BIOFunctor3[FR])(implicit BIOFork: BIOFork3[FR]): BIOFork.type = BIOFork
  @inline implicit final def AttachBlockingIO3[FR[-_, +_, +_]](@deprecated("unused", "") self: BIOFunctor3[FR])(implicit BlockingIO: BlockingIO3[FR]): BlockingIO.type = BlockingIO

  implicit final class KleisliSyntaxAttached[FR[-_, +_, +_]](private val FR0: BIOFunctor3[FR]) extends AnyVal {
    @inline def fromKleisli[R, E, A](k: Kleisli[FR[Any, E, ?], R, A])(implicit FR: BIOMonadAsk[FR]): FR[R, E, A] = FR.fromKleisli(k)
    @inline def toKleisli[R, E, A](fr: FR[R, E, A])(implicit FR: BIOLocal[FR]): Kleisli[FR[Any, E, ?], R, A] = FR.toKleisli(fr)
  }
}

sealed trait BIOFunctorInstancesLowPriority1 extends BIOFunctorInstancesLowPriority2 {
  @inline implicit final def BIOConvertFromBIOAsk[FR[-_, +_, +_]](implicit BIOAsk: NotPredefined.Of[BIOAsk[FR]]): BIOApplicative3[FR] with S2 = S2(BIOAsk.InnerF)

  @inline implicit final def AttachBIOArrowChoice[FR[-_, +_, +_]](@deprecated("unused", "") self: BIOFunctor3[FR])(implicit BIOArrowChoice: BIOArrowChoice[FR]): BIOArrowChoice.type = BIOArrowChoice
  @inline implicit final def AttachBIOArrow[FR[-_, +_, +_]](@deprecated("unused", "") self: BIOFunctor3[FR])(implicit BIOArrow: BIOArrow[FR]): BIOArrow.type = BIOArrow
  @inline implicit final def AttachBIOMonadAsk[FR[-_, +_, +_], R](@deprecated("unused", "") self: BIOFunctor3[FR])(implicit BIOMonadAsk: BIOMonadAsk[FR]): BIOMonadAsk.type = BIOMonadAsk
}

sealed trait BIOFunctorInstancesLowPriority2 extends BIOFunctorInstancesLowPriority3 {
  @inline implicit final def BIOConvertFromBIOProfunctor[FR[-_, +_, +_]](implicit BIOProfunctor: NotPredefined.Of[BIOProfunctor[FR]]): BIOFunctor3[FR] with S3 = S3(BIOProfunctor.InnerF)

  @inline implicit final def AttachBIOProfunctor[FR[-_, +_, +_]](@deprecated("unused", "") self: BIOFunctor3[FR])(implicit BIOProfunctor: BIOProfunctor[FR]): BIOProfunctor.type = BIOProfunctor
  @inline implicit final def AttachBIOAsk[FR[-_, +_, +_], R](@deprecated("unused", "") self: BIOFunctor3[FR])(implicit BIOAsk: BIOAsk[FR]): BIOAsk.type = BIOAsk
  @inline implicit final def AttachBIOBifunctor[FR[-_, +_, +_], R](@deprecated("unused", "") self: BIOFunctor3[FR])(implicit BIOBifunctor: BIOBifunctor3[FR]): BIOBifunctor.type = BIOBifunctor
}

sealed trait BIOFunctorInstancesLowPriority3 extends BIOFunctorInstancesLowPriority4 {
  @inline implicit final def BIOConvertFromBIOBifunctor[FR[-_, +_, +_]](implicit BIOBifunctor: NotPredefined.Of[BIOBifunctor3[FR]]): BIOFunctor3[FR] with S4 = S4(BIOBifunctor.InnerF)
}

sealed trait BIOFunctorInstancesLowPriority4 extends BIOFunctorInstancesLowPriority5 {
  // place ZIO instance at the root of the hierarchy, so that it's visible when summoning any class in hierarchy
  @inline implicit final def BIOZIO: Predefined.Of[BIOAsync3[ZIO]] = Predefined(BIOAsyncZio)
}

sealed trait BIOFunctorInstancesLowPriority5 {
  @inline implicit final def BIOConvert3To2[C[_[-_, +_, +_]], FR[-_, +_, +_], R0](
    implicit BIOFunctor3: C[FR] with BIOFunctor3[FR] {
      type Divergence = Nondivergent
    }
  ): C[Lambda[(`-R`, `+E`, `+A`) => FR[R0, E, A]]] with BIOFunctor[FR[R0, +?, +?]] {
    type Divergence = Divergent
  } = BIOFunctor3.asInstanceOf[C[Lambda[(`-R`, `+E`, `+A`) => FR[R0, E, A]]] with BIOFunctor[FR[R0, +?, +?]] { type Divergence = Divergent }]
}
