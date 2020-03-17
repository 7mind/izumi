package izumi.functional.bio

import cats.data.Kleisli
import izumi.functional.bio.DivergenceHelper.{Divergent, Nondivergent}
import izumi.functional.bio.SpecificityHelper.{S1, S2}
import izumi.functional.bio.impl.BIOAsyncZio
import zio.ZIO

import scala.language.implicitConversions

trait BIOFunctor3[F[-_, +_, +_]] extends BIOFunctorInstances with DivergenceHelper {
  def map[R, E, A, B](r: F[R, E, A])(f: A => B): F[R, E, B]

  def as[R, E, A, B](r: F[R, E, A])(v: => B): F[R, E, B] = map(r)(_ => v)
  def void[R, E, A](r: F[R, E, A]): F[R, E, Unit] = map(r)(_ => ())
  @inline final def widen[R, E, A, A1](r: F[R, E, A])(implicit @deprecated("unused", "") ev: A <:< A1): F[R, E, A1] = r.asInstanceOf[F[R, E, A1]]
}

private[bio] sealed trait BIOFunctorInstances
object BIOFunctorInstances extends BIOFunctorInstancesLowPriority1 {
  @inline implicit final def BIOConvertFromBIOMonadAsk[FR[-_, +_, +_]](implicit self: BIOMonadAsk[FR]): BIOMonad3[FR] with S1 = S1(self.InnerF)

  implicit final class KleisliSyntaxAttached[FR[-_, +_, +_]](private val FR0: BIOFunctor3[FR]) extends AnyVal {
    @inline def fromKleisli[R, E, A](k: Kleisli[FR[Any, E, ?], R, A])(implicit FR: BIOMonadAsk[FR]): FR[R, E, A] = FR.fromKleisli(k)
    @inline def toKleisli[R, E, A](fr: FR[R, E, A])(implicit FR: BIOLocal[FR]): Kleisli[FR[Any, E, ?], R, A] = FR.toKleisli(fr)
  }
}

sealed trait BIOFunctorInstancesLowPriority1 extends BIOFunctorInstancesLowPriority2 {
  @inline implicit final def BIOConvertFromBIOAsk[FR[-_, +_, +_]](implicit self: BIOAsk[FR]): BIOApplicative3[FR] with S2 = S2(self.InnerF)

  @inline implicit final def AttachBIOAsk[FR[-_, +_, +_]](@deprecated("unused", "") self: BIOFunctor3[FR])(implicit BIOAsk: BIOAsk[FR]): BIOAsk.type = BIOAsk
  @inline implicit final def AttachBIOPrimitives3[FR[-_, +_, +_]](@deprecated("unused", "") self: BIOFunctor3[FR])(implicit BIOPrimitives: BIOPrimitives3[FR]): BIOPrimitives.type = BIOPrimitives
  @inline implicit final def AttachBIOFork3[FR[-_, +_, +_]](@deprecated("unused", "") self: BIOFunctor3[FR])(implicit BIOFork: BIOFork3[FR]): BIOFork.type = BIOFork
  @inline implicit final def AttachBlockingIO3[FR[-_, +_, +_]](@deprecated("unused", "") self: BIOFunctor3[FR])(implicit BlockingIO: BlockingIO3[FR]): BlockingIO.type = BlockingIO
}

sealed trait BIOFunctorInstancesLowPriority2 {
  // place ZIO instance at the root of the hierarchy, so that it's visible when summoning any class in hierarchy
  @inline implicit final def BIOZIO: BIOAsync3[ZIO] = BIOAsyncZio
//  @inline implicit final def BIOZIOR[R]: BIOAsync[ZIO[R, +?, +?]] = convert3To2(BIOAsyncZio)

  @inline implicit final def BIOConvert3To2[C[_[-_, +_, +_]], FR[-_, +_, +_], R0](
    implicit BIOFunctor3: C[FR] with BIOFunctor3[FR] {
      type Divergence = Nondivergent
    }
  ): C[Lambda[(`-R`, `+E`, `+A`) => FR[R0, E, A]]] with BIOFunctor[FR[R0, +?, +?]] {
    type Divergence = Divergent
  } = BIOFunctor3.asInstanceOf[C[Lambda[(`-R`, `+E`, `+A`) => FR[R0, E, A]]] with BIOFunctor[FR[R0, +?, +?]] { type Divergence = Divergent }]
}
