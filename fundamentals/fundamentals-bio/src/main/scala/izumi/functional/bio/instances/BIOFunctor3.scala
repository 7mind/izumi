package izumi.functional.bio.instances

import izumi.functional.bio.impl.BIOAsyncZio
import izumi.functional.bio.{BIOAsync, BIOFork, BIOFunctor, BIOPrimitives3, BlockingIO, BlockingIO3}
import zio.{IO, ZIO}

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
  @inline implicit final def BIOZIO3: BIOAsync3[ZIO] = BIOAsyncZio

  @inline implicit final def AttachBIOPrimitives3[F[-_, +_, +_]](@deprecated("unused", "") self: BIOFunctor3[F])(
    implicit BIOPrimitives: BIOPrimitives3[F]
  ): BIOPrimitives.type = BIOPrimitives

  @inline implicit final def AttachBIOFork3[F[-_, +_, +_]](@deprecated("unused", "") self: BIOFunctor3[F])(implicit BIOFork: BIOFork3[F]): BIOFork.type =
    BIOFork

  @inline implicit final def AttachBlockingIO3[F[-_, +_, +_]](@deprecated("unused", "") self: BIOFunctor3[F])(
    implicit BlockingIO: BlockingIO3[F]
  ): BlockingIO.type = BlockingIO
}
