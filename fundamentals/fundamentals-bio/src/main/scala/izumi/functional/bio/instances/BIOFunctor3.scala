package izumi.functional.bio.instances

import izumi.functional.bio.impl.BIOAsyncZio
import izumi.functional.bio._
import zio.ZIO

import scala.language.implicitConversions

trait BIOFunctor3[F[_, _, +_]] extends BIOFunctorInstances {
  def map[R, E, A, B](r: F[R, E, A])(f: A => B): F[R, E, B]

  def as[R, E, A, B](r: F[R, E, A])(v: => B): F[R, E, B] = map(r)(_ => v)
  def void[R, E, A](r: F[R, E, A]): F[R, E, Unit] = map(r)(_ => ())
  @inline final def widen[R, E, A, A1](r: F[R, E, A])(implicit @deprecated("unused", "") ev: A <:< A1): F[R, E, A1] = r.asInstanceOf[F[R, E, A1]]
}

private[bio] sealed trait BIOFunctorInstances
object BIOFunctorInstances extends BIOFunctorInstancesLowPriority {
  // place ZIO instance at the root of the hierarchy, so that it's visible when summoning any class in hierarchy
  @inline implicit final def BIOZIOR[R0]: BIOAsync[ZIO[R0, +?, +?]] = BIOAsyncZio.asInstanceOf[BIOAsync[ZIO[R0, +?, +?]]]

  @inline implicit final def AttachBIOPrimitives[F[+_, +_]](@deprecated("unused", "") self: BIOFunctor[F])(implicit BIOPrimitives: BIOPrimitives[F]): BIOPrimitives.type = BIOPrimitives
  @inline implicit final def AttachBIOFork[F[+_, +_]](@deprecated("unused", "") self: BIOFunctor[F])(implicit BIOFork: BIOFork[F]): BIOFork.type = BIOFork
  @inline implicit final def AttachBlockingIO[F[+_, +_]](@deprecated("unused", "") self: BIOFunctor[F])(implicit BlockingIO: BlockingIO[F]): BlockingIO.type = BlockingIO
}

sealed trait BIOFunctorInstancesLowPriority {
  @inline implicit final def BIOZIO: BIOAsync3[ZIO] = BIOAsyncZio

  @inline implicit final def AttachBIOAsk[FR[-_, +_, +_]](@deprecated("unused", "") self: BIOFunctor3[FR])(implicit BIOAsk: BIOAsk[FR]): BIOAsk.type = BIOAsk

  @inline implicit final def AttachBIOPrimitives3[FR[-_, +_, +_]](@deprecated("unused", "") self: BIOFunctor3[FR])(implicit BIOPrimitives: BIOPrimitives3[FR]): BIOPrimitives.type = BIOPrimitives
  @inline implicit final def AttachBIOFork3[FR[-_, +_, +_]](@deprecated("unused", "") self: BIOFunctor3[FR])(implicit BIOFork: BIOFork3[FR]): BIOFork.type = BIOFork
  @inline implicit final def AttachBlockingIO3[FR[-_, +_, +_]](@deprecated("unused", "") self: BIOFunctor3[FR])(implicit BlockingIO: BlockingIO3[FR]): BlockingIO.type = BlockingIO
}
