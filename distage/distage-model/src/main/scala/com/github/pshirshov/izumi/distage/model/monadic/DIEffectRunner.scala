package com.github.pshirshov.izumi.distage.model.monadic

import cats.effect.IO
import com.github.pshirshov.izumi.functional.bio.BIORunner
import com.github.pshirshov.izumi.fundamentals.platform.functional.Identity

trait DIEffectRunner[F[_]] {
  def run[A](f: F[A]): A
}

object DIEffectRunner {
  def apply[F[_]: DIEffectRunner]: DIEffectRunner[F] = implicitly

  implicit object IdentityImpl extends DIEffectRunner[Identity] {
    override def run[A](f: Identity[A]): A = f
  }

  implicit object CatsIOImpl extends DIEffectRunner[IO] {
    override def run[A](f: IO[A]): A = f.unsafeRunSync()
  }

  final class BIOImpl[F[_, _]: BIORunner] extends DIEffectRunner[F[Throwable, ?]] {
    override def run[A](f: F[Throwable, A]): A = BIORunner[F].unsafeRun(f)
  }

  implicit def bio[F[_, _]: BIORunner]: DIEffectRunner[F[Throwable, ?]] = new BIOImpl[F]
}
