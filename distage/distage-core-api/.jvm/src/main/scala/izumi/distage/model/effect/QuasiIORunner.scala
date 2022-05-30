package izumi.distage.model.effect

import izumi.distage.model.effect.QuasiIORunner.{CatsDispatcherImpl, CatsIOImpl}
import izumi.functional.bio.UnsafeRun2
import izumi.fundamentals.orphans.{`cats.effect.IO`, `cats.effect.std.Dispatcher`, `cats.effect.unsafe.IORuntime`}
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

  implicit object IdentityImpl extends QuasiIORunner[Identity] {
    override def run[A](f: => A): A = f
  }

  implicit def fromBIO[F[_, _]: UnsafeRun2]: QuasiIORunner[F[Throwable, _]] = new BIOImpl[F]

  final class BIOImpl[F[_, _]: UnsafeRun2] extends QuasiIORunner[F[Throwable, _]] {
    override def run[A](f: => F[Throwable, A]): A = UnsafeRun2[F].unsafeRun(f)
  }

  final class CatsIOImpl(implicit ioRuntime: cats.effect.unsafe.IORuntime) extends QuasiIORunner[cats.effect.IO] {
    override def run[A](f: => cats.effect.IO[A]): A = f.unsafeRunSync()(ioRuntime)
  }

  final class CatsDispatcherImpl[F[_]](implicit dispatcher: cats.effect.std.Dispatcher[F]) extends QuasiIORunner[F] {
    override def run[A](f: => F[A]): A = dispatcher.unsafeRunSync(f)
  }

}

private[effect] sealed trait LowPriorityQuasiIORunnerInstances extends LowPriorityQuasiIORunnerInstances1 {

  implicit def fromCatsDispatcher[F[_], Dispatcher[_[_]]: `cats.effect.std.Dispatcher`](implicit dispatcher: Dispatcher[F]): QuasiIORunner[F] =
    new CatsDispatcherImpl[F]()(dispatcher.asInstanceOf[cats.effect.std.Dispatcher[F]])

  def mkFromCatsDispatcher[F[_]](dispatcher: cats.effect.std.Dispatcher[F]): QuasiIORunner[F] = new CatsDispatcherImpl[F]()(dispatcher)

}

private[effect] sealed trait LowPriorityQuasiIORunnerInstances1 {

  implicit def fromCatsIORuntime[IO[_]: `cats.effect.IO`, IORuntime: `cats.effect.unsafe.IORuntime`](implicit ioRuntime: IORuntime): QuasiIORunner[IO] =
    new CatsIOImpl()(ioRuntime.asInstanceOf[cats.effect.unsafe.IORuntime]).asInstanceOf[QuasiIORunner[IO]]

  def mkFromCatsIORuntime(ioRuntime: cats.effect.unsafe.IORuntime): QuasiIORunner[cats.effect.IO] = new CatsIOImpl()(ioRuntime)

}
