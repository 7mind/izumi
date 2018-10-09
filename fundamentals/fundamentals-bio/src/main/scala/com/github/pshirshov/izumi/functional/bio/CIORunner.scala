package com.github.pshirshov.izumi.functional.bio

import scala.language.higherKinds

trait CIORunner[CIO[_]] {
  def unsafeRunSync[A](cio: CIO[A]): A
  def unsafeRunAsync[A](cio: CIO[A])(cb: Either[Throwable, A] => Unit): Unit
}

object CIORunner {
  def apply[CIO[_]: CIORunner]: CIORunner[CIO] = implicitly

  implicit object CatsRunner extends CIORunner[cats.effect.IO] {
    override def unsafeRunSync[A](cio: cats.effect.IO[A]): A = cio.unsafeRunSync()

    override def unsafeRunAsync[A](cio: cats.effect.IO[A])(cb: Either[Throwable, A] => Unit): Unit = cio.unsafeRunAsync(cb)
  }
}
