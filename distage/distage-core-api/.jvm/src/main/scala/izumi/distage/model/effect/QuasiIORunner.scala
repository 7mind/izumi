package izumi.distage.model.effect

import izumi.distage.model.effect.QuasiIORunner.CatsImpl
import izumi.functional.bio.UnsafeRun2
import izumi.fundamentals.orphans.`cats.effect.Effect`
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.language.unused

/**
  * An `unsafeRun` for `F`. Required for `distage-framework` apps and `distage-testkit` tests,
  * but will be provided automatically by [[izumi.distage.modules.DefaultModule]] for all existing Scala effect types.
  *
  * Unlike `QuasiIO` there's nothing "quasi" about it – it makes sense. But named like that for consistency anyway.
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

  implicit def fromBIO[F[_, _]: UnsafeRun2]: QuasiIORunner[F[Throwable, ?]] = new BIOImpl[F]

  final class BIOImpl[F[_, _]: UnsafeRun2] extends QuasiIORunner[F[Throwable, ?]] {
    override def run[A](f: => F[Throwable, A]): A = UnsafeRun2[F].unsafeRun(f)
  }

  final class CatsImpl[F[_]: cats.effect.Effect] extends QuasiIORunner[F] {
    override def run[A](f: => F[A]): A = cats.effect.Effect[F].toIO(f).unsafeRunSync()
  }

}

private[effect] sealed trait LowPriorityQuasiIORunnerInstances {

  implicit def fromCats[F[_], Effect[_[_]]](implicit @unused l: `cats.effect.Effect`[Effect], F: Effect[F]): QuasiIORunner[F] =
    new CatsImpl[F]()(F.asInstanceOf[cats.effect.Effect[F]])

}
