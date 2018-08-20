package com.github.pshirshov.izumi.idealingua.runtime.rpc.http4s

import scalaz.zio.{ExitResult, IO, RTS}

import scala.language.higherKinds

trait BIORunner[ZIO[+ _, + _]] {
  def unsafeRun[E, A](io: ZIO[E, A]): A
  def unsafeRunSync0[E, A](io: ZIO[E, A]): ExitResult[E, A]
}

object BIORunner {

  implicit object ZIOR extends BIORunner[IO] with RTS {
    override def defaultHandler: List[Throwable] => IO[Nothing, Unit] = {
      _ => IO.sync(())
    }

    def unsafeRunSync0[E, A](io: IO[E, A]): ExitResult[E, A] = unsafeRunSync(io)
  }

}
