package izumi.functional.quasi

import cats.effect.IO
import izumi.functional.bio.UnsafeRun2
import izumi.functional.bio.data.Morphism1
import izumi.fundamentals.platform.concurrent.IzFuture.toRichFuture
import izumi.fundamentals.platform.functional.Identity

import scala.concurrent.Future

/**
  * An `unsafeRun` for `F`. Required for `distage-framework` apps and `distage-testkit` tests,
  * but is provided automatically by [[izumi.distage.modules.DefaultModule]] for all existing Scala effect types.
  *
  * Unlike `QuasiIO` there's nothing 'quasi' about it – it makes sense. But named like that for consistency anyway.
  *
  * Internal use class, as with [[QuasiIO]], it's only public so that you can define your own instances,
  * better use [[izumi.functional.bio]] or [[cats]] typeclasses for application logic.
  */
trait QuasiIORunner[F[_]] { self =>
  def runBlocking[A](f: => F[A]): A
  def runFuture[A](f: => F[A]): Future[A]
  def runFutureInterruptible[A](f: => F[A]): (Future[A], () => Future[Unit])
}

object QuasiIORunner extends LowPriorityQuasiIORunnerInstances {
  def apply[F[_]: QuasiIORunner]: QuasiIORunner[F] = implicitly

  implicit object IdentityImpl extends QuasiIORunner[Identity] {
    private final val IdentityThreadNamePrefix = "quasi-identity-runner"
    override def runBlocking[A](f: => A): A = f
    override def runFuture[A](f: => A): Future[A] = Future.successful(f)
    override def runFutureInterruptible[A](f: => A): (Future[A], () => Future[Unit]) = {
      val promise = scala.concurrent.Promise[A]()
      val thread = new Thread(
        new Runnable {
          override def run(): Unit = {
            try {
              val result = f
              promise.trySuccess(result)
              ()
            } catch {
              case t: Throwable =>
                promise.tryFailure(t)
                ()
            }
          }
        },
        s"$IdentityThreadNamePrefix-${java.util.UUID.randomUUID()}",
      )
      thread.setDaemon(true)
      thread.start()
      val interruptAction = () => {
        thread.interrupt()
        Future.unit
      }
      (promise.future, interruptAction)
    }
  }

  implicit def fromBIO[F[+_, +_]: UnsafeRun2]: QuasiIORunner[F[Throwable, _]] = new BIOImpl[F]

  def mkFromCatsIORuntime(ioRuntime: cats.effect.unsafe.IORuntime): QuasiIORunner[cats.effect.IO] = new CatsIOImpl()(using ioRuntime)

  def mkFromCatsDispatcher[F[_]](dispatcher: cats.effect.std.Dispatcher[F]): QuasiIORunner[F] = new CatsDispatcherImpl[F]()(using dispatcher)

  final class BIOImpl[F[+_, +_]: UnsafeRun2] extends QuasiIORunner[F[Throwable, _]] {
    override def runBlocking[A](f: => F[Throwable, A]): A = UnsafeRun2[F].unsafeRun(f)
    override def runFuture[A](f: => F[Throwable, A]): Future[A] = {
      UnsafeRun2[F].unsafeRunAsyncAsFuture(f).transformedFuture(_.flatMap(_.toTry))
    }
    override def runFutureInterruptible[A](f: => F[Throwable, A]): (Future[A], () => Future[Unit]) = {
      val (future, interruptAction) = UnsafeRun2[F].unsafeRunAsyncAsInterruptibleFuture(f)
      (future.transformedFuture(_.flatMap(_.toTry)), () => runFuture(interruptAction.interrupt))
    }
  }

  final class CatsIOImpl()(implicit ioRuntime: cats.effect.unsafe.IORuntime) extends QuasiIORunner[cats.effect.IO] {
    override def runBlocking[A](f: => cats.effect.IO[A]): A = f.unsafeRunSync()(using ioRuntime)
    override def runFuture[A](f: => IO[A]): Future[A] = f.unsafeToFuture()(using ioRuntime)
    override def runFutureInterruptible[A](f: => IO[A]): (Future[A], () => Future[Unit]) = {
      f.unsafeToFutureCancelable()(using ioRuntime)
    }
  }

  final class CatsDispatcherImpl[F[_]]()(implicit dispatcher: cats.effect.std.Dispatcher[F]) extends QuasiIORunner[F] {
    override def runBlocking[A](f: => F[A]): A = dispatcher.unsafeRunSync(f)
    override def runFuture[A](f: => F[A]): Future[A] = dispatcher.unsafeToFuture(f)
    override def runFutureInterruptible[A](f: => F[A]): (Future[A], () => Future[Unit]) = dispatcher.unsafeToFutureCancelable(f)
  }

  implicit class QuasiIORunnerOps[F[_]](private val runner: QuasiIORunner[F]) extends AnyVal {
    def contramapK[G[_]](g: Morphism1[G, F]): QuasiIORunner[G] = new QuasiIORunner[G] {
      override def runBlocking[A](f: => G[A]): A = runner.runBlocking(g(f))
      override def runFuture[A](f: => G[A]): Future[A] = runner.runFuture(g(f))
      override def runFutureInterruptible[A](f: => G[A]): (Future[A], () => Future[Unit]) = runner.runFutureInterruptible(g(f))
    }
  }

}
