package izumi.functional.bio

import cats.effect.IO
import izumi.functional.bio.data.Morphism1
import izumi.fundamentals.platform.concurrent.IzFuture.toRichFuture
import izumi.fundamentals.platform.functional.Identity

import scala.concurrent.Future

/**
  * Scala.js does not support running effects synchronously so only async interface is available
  */
trait IORunner1[F[_]] {
  def runFuture[A](f: => F[A]): Future[A]
  def runFutureInterruptible[A](f: => F[A]): (Future[A], () => Future[Unit])
}

object IORunner1 extends LowPriorityIORunner1Instances {
  @inline def apply[F[_]](implicit ev: IORunner1[F]): IORunner1[F] = ev

  implicit object IdentityImpl extends IORunner1[Identity] {
    override def runFuture[A](f: => Identity[A]): Future[A] = Future.successful(f)
    override def runFutureInterruptible[A](f: => A): (Future[A], () => Future[Unit]) = (Future.successful(f), () => Future.unit)
  }

  implicit def fromBIO[F[+_, +_]: UnsafeRun2]: IORunner1[F[Throwable, _]] = new BIOImpl[F]

  def mkFromCatsIORuntime(ioRuntime: cats.effect.unsafe.IORuntime): IORunner1[cats.effect.IO] = new CatsIOImpl()(using ioRuntime)

  def mkFromCatsDispatcher[F[_]](dispatcher: cats.effect.std.Dispatcher[F]): IORunner1[F] = new CatsDispatcherImpl[F]()(using dispatcher)

  final class BIOImpl[F[+_, +_]: UnsafeRun2] extends IORunner1[F[Throwable, _]] {
    override def runFuture[A](f: => F[Throwable, A]): Future[A] = {
      UnsafeRun2[F].unsafeRunAsyncAsFuture(f).transformedFuture(_.flatMap(_.toTry))
    }
    override def runFutureInterruptible[A](f: => F[Throwable, A]): (Future[A], () => Future[Unit]) = {
      val (future, interruptAction) = UnsafeRun2[F].unsafeRunAsyncAsInterruptibleFuture(f)
      (future.transformedFuture(_.flatMap(_.toTry)), () => runFuture(interruptAction.interrupt))
    }
  }

  final class CatsIOImpl()(implicit ioRuntime: cats.effect.unsafe.IORuntime) extends IORunner1[cats.effect.IO] {
    override def runFuture[A](f: => IO[A]): Future[A] = f.unsafeToFuture()(using ioRuntime)
    override def runFutureInterruptible[A](f: => IO[A]): (Future[A], () => Future[Unit]) = {
      f.unsafeToFutureCancelable()(using ioRuntime)
    }
  }

  final class CatsDispatcherImpl[F[_]]()(implicit dispatcher: cats.effect.std.Dispatcher[F]) extends IORunner1[F] {
    override def runFuture[A](f: => F[A]): Future[A] = dispatcher.unsafeToFuture(f)
    override def runFutureInterruptible[A](f: => F[A]): (Future[A], () => Future[Unit]) = dispatcher.unsafeToFutureCancelable(f)
  }

  implicit class IORunner1Ops[F[_]](private val runner: IORunner1[F]) extends AnyVal {
    def contramapK[G[_]](g: Morphism1[G, F]): IORunner1[G] = new IORunner1[G] {
      override def runFuture[A](f: => G[A]): Future[A] = runner.runFuture(g(f))
      override def runFutureInterruptible[A](f: => G[A]): (Future[A], () => Future[Unit]) = runner.runFutureInterruptible(g(f))
    }
  }
}
