package izumi.distage.model.effect

import izumi.distage.model.effect.QuasiEffectRunner.CatsImpl
import izumi.functional.bio.BIORunner
import izumi.fundamentals.orphans.`cats.effect.Effect`
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.language.unused

trait QuasiEffectRunner[F[_]] {
  def run[A](f: => F[A]): A
}

object QuasiEffectRunner extends LowPriorityQuasiEffectRunnerInstances {
  def apply[F[_]: QuasiEffectRunner]: QuasiEffectRunner[F] = implicitly

  implicit object IdentityImpl extends QuasiEffectRunner[Identity] {
    override def run[A](f: => A): A = f
  }

  implicit def fromBIO[F[_, _]: BIORunner]: QuasiEffectRunner[F[Throwable, ?]] = new BIOImpl[F]

  final class BIOImpl[F[_, _]: BIORunner] extends QuasiEffectRunner[F[Throwable, ?]] {
    override def run[A](f: => F[Throwable, A]): A = BIORunner[F].unsafeRun(f)
  }

  final class CatsImpl[F[_]: cats.effect.Effect] extends QuasiEffectRunner[F] {
    override def run[A](f: => F[A]): A = cats.effect.Effect[F].toIO(f).unsafeRunSync()
  }

}

private[effect] sealed trait LowPriorityQuasiEffectRunnerInstances {

  implicit def fromCats[F[_], Effect[_[_]]](implicit @unused l: `cats.effect.Effect`[Effect], F: Effect[F]): QuasiEffectRunner[F] =
    new CatsImpl[F]()(F.asInstanceOf[cats.effect.Effect[F]])

}
