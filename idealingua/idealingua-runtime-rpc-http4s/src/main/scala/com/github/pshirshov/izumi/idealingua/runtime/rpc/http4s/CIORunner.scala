package com.github.pshirshov.izumi.idealingua.runtime.rpc.http4s

import scala.language.higherKinds

trait CIORunner[CIO[+_]] {
  def unsafeRunSync[A](cio: CIO[A]): A
}

object CIORunner {
  implicit object CatsRunner extends CIORunner[cats.effect.IO] {
    override def unsafeRunSync[A](cio: cats.effect.IO[A]): A = cio.unsafeRunSync()
  }
}
