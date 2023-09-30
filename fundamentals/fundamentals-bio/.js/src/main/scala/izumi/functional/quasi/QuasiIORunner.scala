package izumi.functional.quasi

import cats.effect.IO
import izumi.functional.bio.{Exit, UnsafeRun2}
import izumi.fundamentals.platform.functional.Identity

import scala.concurrent.{ExecutionContext, Future}

/**
  * Scala.js does not support running effects synchronously so only async interface is available
  */
trait QuasiIORunner[F[_]] {
  def runFuture[A](f: => F[A]): Future[A]
}

object QuasiIORunner extends LowPriorityQuasiIORunnerInstances {
  @inline def apply[F[_]](implicit ev: QuasiIORunner[F]): QuasiIORunner[F] = ev

  implicit object IdentityImpl extends QuasiIORunner[Identity] {
    override def runFuture[A](f: => Identity[A]): Future[A] = Future.successful(f)
  }

  final class BIOImpl[F[_, _]: UnsafeRun2](implicit val ec: ExecutionContext) extends QuasiIORunner[F[Throwable, _]] {
    override def runFuture[A](f: => F[Throwable, A]): Future[A] = UnsafeRun2[F].unsafeRunAsyncAsFuture(f).flatMap {
      case Exit.Success(value) =>
        Future.successful(value)
      case failure: Exit.Failure[Throwable] @unchecked =>
        Future.failed(failure.toThrowable(t => t))
    }
  }

  final class CatsIOImpl(implicit ioRuntime: cats.effect.unsafe.IORuntime) extends QuasiIORunner[cats.effect.IO] {
    override def runFuture[A](f: => IO[A]): Future[A] = f.unsafeToFuture()
  }

  final class CatsDispatcherImpl[F[_]](implicit dispatcher: cats.effect.std.Dispatcher[F]) extends QuasiIORunner[F] {
    override def runFuture[A](f: => F[A]): Future[A] = dispatcher.unsafeToFuture(f)
  }
}
