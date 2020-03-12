package izumi.functional.bio.instances

import izumi.functional.bio.impl.BIOAsyncZio
import izumi.functional.bio.{BIOAsync, BIOFork, BIOFunctor, BIOPrimitives3, BlockingIO, BlockingIO3}
import zio.ZIO

import scala.language.implicitConversions

trait BIOFunctor3[F[-_, _, +_]] extends BIOFunctorInstances {
  def map[R, E, A, B](r: F[R, E, A])(f: A => B): F[R, E, B]

  def as[R, E, A, B](r: F[R, E, A])(v: => B): F[R, E, B] = map(r)(_ => v)
  def void[R, E, A](r: F[R, E, A]): F[R, E, Unit] = map(r)(_ => ())
  @inline final def widen[R, E, A, A1](r: F[R, E, A])(implicit @deprecated("unused", "") ev: A <:< A1): F[R, E, A1] = r.asInstanceOf[F[R, E, A1]]
}

private[bio] sealed trait BIOFunctorInstances
object BIOFunctorInstances {
  // place ZIO instance at the root of the hierarchy, so that it's visible when summoning any class in hierarchy
  @inline implicit final def BIOZIO[R]: BIOAsync[ZIO[R, +?, +?]] = BIOAsyncZio.asInstanceOf[BIOAsync[ZIO[R, +?, +?]]]
  @inline implicit final def BIOZIO3: BIOAsyncZio = BIOAsyncZio.asInstanceOf[BIOAsyncZio]

  @inline implicit final def AttachBIOPrimitives[F[+_, +_]](@deprecated("unused", "") self: BIOFunctor[F])(
    implicit BIOPrimitives: BIOPrimitives[F]
  ): BIOPrimitives.type = BIOPrimitives
  @inline implicit final def AttachBIOPrimitives3[F[-_, +_, +_]](@deprecated("unused", "") self: BIOFunctor3[F])(
    implicit BIOPrimitives: BIOPrimitives3[F]
  ): BIOPrimitives.type = BIOPrimitives

  @inline implicit final def AttachBIOFork[F[+_, +_]](@deprecated("unused", "") self: BIOFunctor[F])(implicit BIOFork: BIOFork[F]): BIOFork.type = BIOFork
  @inline implicit final def AttachBIOFork3[F[-_, +_, +_]](@deprecated("unused", "") self: BIOFunctor3[F[-?, +?, +?]])(implicit BIOFork: BIOFork3[F]): BIOFork.type =
    BIOFork

  @inline implicit final def AttachBlockingIO[F[+_, +_]](@deprecated("unused", "") self: BIOFunctor[F])(implicit BlockingIO: BlockingIO[F]): BlockingIO.type =
    BlockingIO
  @inline implicit final def AttachBlockingIO3[F[-_, +_, +_]](@deprecated("unused", "") self: BIOFunctor3[F[-?, +?, +?]])(
    implicit BlockingIO: BlockingIO3[F]
  ): BlockingIO.type = BlockingIO
}
