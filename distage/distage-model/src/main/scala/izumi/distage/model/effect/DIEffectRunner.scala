package izumi.distage.model.effect

import cats.effect.IO
import izumi.functional.bio.BIORunner
import izumi.fundamentals.platform.functional.Identity

trait DIEffectRunner[F[_]] {
  def run[A](f: => F[A]): A
}

object DIEffectRunner {
  def apply[F[_]: DIEffectRunner]: DIEffectRunner[F] = implicitly

  implicit object IdentityImpl extends DIEffectRunner[Identity] {
    override def run[A](f: => A): A = f
  }

  implicit object CatsIOImpl extends DIEffectRunner[IO] {
    override def run[A](f: => IO[A]): A = f.unsafeRunSync()
  }

  implicit def bio[F[_, _]: BIORunner]: DIEffectRunner[F[Throwable, ?]] = new BIOImpl[F]

  final class BIOImpl[F[_, _]: BIORunner] extends DIEffectRunner[F[Throwable, ?]] {
    override def run[A](f: => F[Throwable, A]): A = BIORunner[F].unsafeRun(f)
  }

}
