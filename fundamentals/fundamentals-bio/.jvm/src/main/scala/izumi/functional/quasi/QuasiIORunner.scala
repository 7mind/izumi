package izumi.functional.quasi

import izumi.functional.bio.UnsafeRun2
import izumi.fundamentals.platform.functional.Identity

/**
  * An `unsafeRun` for `F`. Required for `distage-framework` apps and `distage-testkit` tests,
  * but is provided automatically by [[izumi.distage.modules.DefaultModule]] for all existing Scala effect types.
  *
  * Unlike `QuasiIO` there's nothing 'quasi' about it – it makes sense. But named like that for consistency anyway.
  *
  * Internal use class, as with [[QuasiIO]], it's only public so that you can define your own instances,
  * better use [[izumi.functional.bio]] or [[cats]] typeclasses for application logic.
  */
trait QuasiIORunner[F[_]] {
  def run[A](f: => F[A]): A
}

object QuasiIORunner extends LowPriorityQuasiIORunnerInstances {
  def apply[F[_]: QuasiIORunner]: QuasiIORunner[F] = implicitly

  given IdentityImpl: QuasiIORunner[Identity] with {
    override def run[A](f: => A): A = f
  }

  given fromBIO[F[_, _]: UnsafeRun2]: QuasiIORunner[F[Throwable, _]] = new BIOImpl[F]

  final class BIOImpl[F[_, _]: UnsafeRun2] extends QuasiIORunner[F[Throwable, _]] {
    override def run[A](f: => F[Throwable, A]): A = UnsafeRun2[F].unsafeRun(f)
  }

  final class CatsIOImpl(using ioRuntime: cats.effect.unsafe.IORuntime) extends QuasiIORunner[cats.effect.IO] {
    override def run[A](f: => cats.effect.IO[A]): A = f.unsafeRunSync()(ioRuntime)
  }

  final class CatsDispatcherImpl[F[_]](using dispatcher: cats.effect.std.Dispatcher[F]) extends QuasiIORunner[F] {
    override def run[A](f: => F[A]): A = dispatcher.unsafeRunSync(f)
  }
}
