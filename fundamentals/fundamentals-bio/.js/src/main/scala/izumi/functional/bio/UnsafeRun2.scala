package izumi.functional.bio

import zio.internal.Platform
import zio.{Runtime, ZIO}

import scala.annotation.unused
import scala.concurrent.{ExecutionContext, Future}

/**
  * Scala.js does not support running effects synchronously so only async interface is available
  */
trait UnsafeRun2[F[_, _]] {
  def unsafeRunFuture[E, A](io: => F[E, A]): Future[Exit[E, A]]
  def unsafeRunAsync[E, A](io: => F[E, A])(callback: Exit[E, A] => Unit): Unit
}

object UnsafeRun2 {
  @inline def apply[F[_, _]](implicit ev: UnsafeRun2[F]): UnsafeRun2[F] = ev

  def createZIO[R](platform: Platform, initialEnv: R): ZIORunner[R] =
    new ZIORunner[R](platform, initialEnv)

  class ZIORunner[R](
    val platform: Platform,
    val initialEnv: R,
  ) extends UnsafeRun2[ZIO[R, +_, +_]] {
    val runtime: Runtime[R] = Runtime(initialEnv, platform)

    override def unsafeRunFuture[E, A](io: => ZIO[R, E, A]): Future[Exit[E, A]] = {
      runtime.unsafeRunToFuture(io.run).map(ze => Exit.ZIOExit.toExit(ze)(outerInterruptionConfirmed = false))(platform.executor.asEC)
    }

    override def unsafeRunAsync[E, A](io: => ZIO[R, E, A])(callback: Exit[E, A] => Unit): Unit = {
      runtime.unsafeRunAsync(io)(ze => callback(Exit.ZIOExit.toExit(ze)(outerInterruptionConfirmed = false)))
    }
  }

  sealed trait FailureHandler
  object FailureHandler {
    case object Default extends FailureHandler
    final case class Custom(handler: Exit.Failure[Any] => Unit) extends FailureHandler
  }

  object NamedThreadFactory {
    final lazy val QuasiAsyncIdentityPool: ExecutionContext = ExecutionContext.Implicits.global
    final def QuasiAsyncIdentityThreadFactory(@unused max: Int): ExecutionContext = QuasiAsyncIdentityPool
  }
}
