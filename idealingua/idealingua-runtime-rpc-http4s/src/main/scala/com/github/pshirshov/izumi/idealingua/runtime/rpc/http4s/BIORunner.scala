package com.github.pshirshov.izumi.idealingua.runtime.rpc.http4s

import scalaz.zio.{ExitResult, IO, RTS}

import scala.language.higherKinds

trait BIORunner[BIO[_, _]] {
  def unsafeRun[E, A](io: BIO[E, A]): A
  def unsafeRunSync0[E, A](io: BIO[E, A]): ExitResult[E, A]
}

object BIORunner {

  implicit object ZIORunner extends BIORunner[IO] with RTS {
    override def defaultHandler: List[Throwable] => IO[Nothing, Unit] = {
      _ => IO.sync(())
    }

    def unsafeRunSync0[E, A](io: IO[E, A]): ExitResult[E, A] = unsafeRunSync(io)
  }

}
