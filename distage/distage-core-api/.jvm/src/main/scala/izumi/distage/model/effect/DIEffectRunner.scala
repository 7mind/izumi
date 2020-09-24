package izumi.distage.model.effect

import izumi.distage.model.effect.DIEffectRunner.CatsImpl
import izumi.functional.bio.BIORunner
import izumi.fundamentals.orphans.`cats.effect.Effect`
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.language.unused

trait DIEffectRunner[F[_]] {
  def run[A](f: => F[A]): A
}

object DIEffectRunner extends LowPriorityDIEffectRunnerInstances {
  def apply[F[_]: DIEffectRunner]: DIEffectRunner[F] = implicitly

  implicit object IdentityImpl extends DIEffectRunner[Identity] {
    override def run[A](f: => A): A = f
  }

  implicit def fromBIO[F[_, _]: BIORunner]: DIEffectRunner[F[Throwable, ?]] = new BIOImpl[F]

  final class BIOImpl[F[_, _]: BIORunner] extends DIEffectRunner[F[Throwable, ?]] {
    override def run[A](f: => F[Throwable, A]): A = BIORunner[F].unsafeRun(f)
  }

  final class CatsImpl[F[_]: cats.effect.Effect] extends DIEffectRunner[F] {
    override def run[A](f: => F[A]): A = cats.effect.Effect[F].toIO(f).unsafeRunSync()
  }

}

private[effect] sealed trait LowPriorityDIEffectRunnerInstances {

  implicit def fromCats[F[_], Effect[_[_]]](implicit @unused l: `cats.effect.Effect`[Effect], F: Effect[F]): DIEffectRunner[F] =
    new CatsImpl[F]()(F.asInstanceOf[cats.effect.Effect[F]])

}
