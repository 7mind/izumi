package izumi.distage.model.effect

import izumi.distage.model.effect.QuasiIORunner.CatsImpl
import izumi.functional.bio.UnsafeRun2
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.orphans.`cats.effect.std.Dispatcher`

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

  final class CatsImpl[F[_]](implicit dispatcher: cats.effect.std.Dispatcher[F]) extends QuasiIORunner[F] {
    override def run[A](f: => F[A]): A = dispatcher.unsafeRunSync(f)
  }

}

private[effect] sealed trait LowPriorityQuasiIORunnerInstances {

  implicit def fromCats[F[_], Dispatcher[_[_]]: `cats.effect.std.Dispatcher`](implicit dispatcher: Dispatcher[F]): QuasiIORunner[F] =
    new CatsImpl[F]()(dispatcher.asInstanceOf[cats.effect.std.Dispatcher[F]])

  def fromCats[F[_]](dispatcher: cats.effect.std.Dispatcher[F]): QuasiIORunner[F] = new CatsImpl[F]()(dispatcher)

}
