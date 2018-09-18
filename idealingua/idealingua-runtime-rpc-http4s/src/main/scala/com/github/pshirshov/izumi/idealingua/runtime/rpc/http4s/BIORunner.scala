package com.github.pshirshov.izumi.idealingua.runtime.rpc.http4s

import scalaz.zio.{ExitResult, IO, RTS}

import scala.language.higherKinds
import scala.util.{Failure, Success, Try}

trait BIORunner[BIO[_, _]] {
  def unsafeRun[E, A](io: BIO[E, A]): A

  @deprecated("use unsafeRunSync1", "17/09/2018")
  def unsafeRunSync0[E, A](io: BIO[E, A]): ExitResult[E, A]

  def unsafeRunSyncAsEither[E, A](io: BIO[E, A]): Try[Either[E, A]]
}

object BIORunner {
  def apply[BIO[_, _]: BIORunner]: BIORunner[BIO] = implicitly

  implicit object ZIORunner extends BIORunner[IO] with RTS {
    override def defaultHandler: List[Throwable] => IO[Nothing, Unit] = {
      _ => IO.sync(())
    }

    @deprecated("use unsafeRunSyncAsEither", "17/09/2018")
    override def unsafeRunSync0[E, A](io: IO[E, A]): ExitResult[E, A] = unsafeRunSync(io)

    override def unsafeRunSyncAsEither[E, A](io: IO[E, A]): Try[Either[E, A]] = unsafeRunSync(io) match {
      case ExitResult.Completed(v) => Success(Right(v))
      case ExitResult.Failed(e, _) => Success(Left(e))
      case ExitResult.Terminated(t) => Failure(t.head)
    }
  }

}
