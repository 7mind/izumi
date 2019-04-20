package com.github.pshirshov.izumi.distage.roles

import cats.effect.IO
import com.github.pshirshov.izumi.fundamentals.platform.functional.Identity

trait DIEffectRunner[F[_]] {
  def run[A](f: F[A]): A
}

object DIEffectRunner {

  implicit object IdentityDIEffectRunner extends DIEffectRunner[Identity] {
    override def run[A](f: Identity[A]): A = f
  }

  implicit object CIODIEffectRunner extends DIEffectRunner[IO] {
    override def run[A](f: IO[A]): A = f.unsafeRunSync()
  }

}
